package service;

import event.TaskEventListener;
import exception.BadGatewayException;
import exception.FieldIssue;
import exception.NotFoundException;
import exception.ValidationException;
import model.Priority;
import model.Task;
import model.TaskRequest;
import parser.BreakdownStrategy;
import parser.ParsedTask;
import parser.TaskParser;
import repository.TaskRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Business logic + validation. Depends only on interfaces. Subject of the Observer pattern. */
public class TaskService {
    private final TaskRepository repository;
    private final TaskParser parser;
    private final BreakdownStrategy breakdown;
    private final List<TaskEventListener> listeners = new CopyOnWriteArrayList<>();

    public TaskService(TaskRepository repository, TaskParser parser, BreakdownStrategy breakdown) {
        this.repository = repository;
        this.parser = parser;
        this.breakdown = breakdown;
    }

    // ---------- Observer ----------

    public void addListener(TaskEventListener l) { listeners.add(l); }

    public void removeListener(TaskEventListener l) { listeners.remove(l); }

    private void publish(String type, Task task) {
        for (TaskEventListener l : listeners) {
            l.onEvent(type, task);
        }
    }

    // ---------- CRUD ----------

    public Task create(TaskRequest request) {
        return createWithParent(request, null);
    }

    private Task createWithParent(TaskRequest request, Integer parentId) {
        Task saved = repository.save(validate(request, new ArrayList<>(), "").parentId(parentId)
                .recurrence(request.getRecurrence()).build());
        publish(TaskEventListener.CREATED, saved);
        return saved;
    }

    public Task get(int id) {
        Task t = repository.findById(id);
        if (t == null) {
            throw new NotFoundException("Task " + id + " not found");
        }
        return t;
    }

    public Task update(int id, TaskRequest request) {
        Task existing = get(id);
        Task changed = validate(request, new ArrayList<>(), "")
                .id(id).parentId(existing.getParentId()).createdAt(existing.getCreatedAt()).build();
        Task saved = repository.save(changed);
        publish(TaskEventListener.UPDATED, saved);
        if (!existing.isCompleted() && saved.isCompleted()) {
            publish(TaskEventListener.COMPLETED, saved);
        }
        return saved;
    }

    /** Deletes the task and (recursively) its subtasks. Returns the number of tasks removed. */
    public int delete(int id) {
        Task t = get(id);
        int removed = 0;
        for (Task child : subtasksOf(id)) {
            removed += delete(child.getId());
        }
        repository.delete(id);
        publish(TaskEventListener.DELETED, t);
        return removed + 1;
    }

    // ---------- AI creation ----------

    /** "Never trust the model": parsed output goes through the same validation; failure -> 502. */
    public Task createFromText(String text, String today) {
        if (text == null || text.isBlank()) {
            throw new ValidationException("text", "must not be empty");
        }
        ParsedTask p = parser.parse(text, today); // throws BadGateway / ServiceUnavailable
        String recurrence = normalizeRecurrence(p.getRecurrence());
        TaskRequest request = new TaskRequest(p.getTitle(), p.getDescription(), "false", p.getDueDate(), null, recurrence);
        try {
            return create(request);
        } catch (ValidationException e) {
            throw new BadGatewayException("Model output failed validation", e.getIssues());
        }
    }

    /** Model-supplied recurrence must be one of a fixed set; anything else is bad model output (502). */
    private static String normalizeRecurrence(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        String value = raw.trim().toLowerCase();
        if (value.equals("daily") || value.equals("weekly") || value.equals("monthly") || value.equals("yearly")) {
            return value;
        }
        throw new BadGatewayException("Model output failed validation",
                List.of(new FieldIssue("recurrence", "must be daily, weekly, monthly or yearly")));
    }

    // ---------- Subtasks ----------

    public Task createSubtask(int parentId, TaskRequest request) {
        Task parent = get(parentId);
        requireTopLevel(parent, "have subtasks");
        return createWithParent(request, parentId);
    }

    public List<Task> listSubtasks(int parentId) {
        get(parentId);
        return subtasksOf(parentId);
    }

    /** All-or-nothing: every item is validated before anything is saved. */
    public List<Task> breakDown(int id) {
        Task parent = get(id);
        requireTopLevel(parent, "be broken down");
        List<TaskRequest> requests = breakdown.breakDown(parent);
        if (requests == null || requests.isEmpty()) {
            throw new BadGatewayException("No subtasks were produced");
        }
        List<FieldIssue> issues = new ArrayList<>();
        List<Task.Builder> builders = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            builders.add(validate(requests.get(i), issues, "subtasks[" + i + "]."));
        }
        if (!issues.isEmpty()) {
            throw new BadGatewayException("Model output failed validation", issues);
        }
        List<Task> saved = new ArrayList<>();
        for (Task.Builder b : builders) {
            Task t = repository.save(b.parentId(id).build());
            saved.add(t);
            publish(TaskEventListener.CREATED, t);
        }
        return saved;
    }

    private void requireTopLevel(Task task, String action) {
        if (task.getParentId() != null) {
            throw new ValidationException("id", "task " + task.getId() + " is a subtask and cannot " + action);
        }
    }

    private List<Task> subtasksOf(int parentId) {
        List<Task> result = new ArrayList<>();
        for (Task t : repository.findAll()) {
            if (t.getParentId() != null && t.getParentId() == parentId) {
                result.add(t);
            }
        }
        return result;
    }

    // ---------- Query ----------

    /** Filters, searches, sorts, then pages - all in one call. */
    public Map<String, Object> list(TaskQuery q) {
        TaskQuery query = q == null ? TaskQuery.builder().build() : q;
        List<FieldIssue> issues = new ArrayList<>();
        List<TaskFilter> filters = new ArrayList<>();

        if (query.getCompleted() != null) {
            if (query.getCompleted().equals("true") || query.getCompleted().equals("false")) {
                filters.add(new Filters.CompletedFilter(Boolean.parseBoolean(query.getCompleted())));
            } else {
                issues.add(new FieldIssue("completed", "must be \"true\" or \"false\""));
            }
        }
        if (query.getPriority() != null) {
            Priority p = parsePriority(query.getPriority());
            if (p == null) {
                issues.add(new FieldIssue("priority", "must be LOW, MEDIUM or HIGH"));
            } else {
                filters.add(new Filters.PriorityFilter(p));
            }
        }
        if (query.getSearch() != null && !query.getSearch().isBlank()) {
            filters.add(new Filters.SearchFilter(query.getSearch().trim()));
        }

        SortStrategy sort = null;
        if (query.getSortBy() != null) {
            if (query.getSortBy().equals("createdAt")) {
                sort = new Sorts.CreatedAtSort();
            } else if (query.getSortBy().equals("dueDate")) {
                sort = new Sorts.DueDateSort();
            } else {
                issues.add(new FieldIssue("sortBy", "must be createdAt or dueDate"));
            }
        }
        boolean ascending = true;
        if (query.getOrder() != null) {
            if (query.getOrder().equals("asc")) {
                ascending = true;
            } else if (query.getOrder().equals("desc")) {
                ascending = false;
            } else {
                issues.add(new FieldIssue("order", "must be asc or desc"));
            }
        }
        if (query.getPage() < 1) {
            issues.add(new FieldIssue("page", "must be 1 or greater"));
        }
        if (query.getPageSize() < 1) {
            issues.add(new FieldIssue("pageSize", "must be 1 or greater"));
        }
        if (!issues.isEmpty()) {
            throw new ValidationException(issues);
        }

        List<Task> matched = new ArrayList<>();
        for (Task t : repository.findAll()) {
            boolean ok = true;
            for (TaskFilter f : filters) {
                if (!f.matches(t)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                matched.add(t);
            }
        }
        if (sort != null) {
            matched.sort(sort.comparator(ascending));
        }

        int total = matched.size();
        int pageSize = query.getPageSize();
        int totalPages = (total + pageSize - 1) / pageSize;
        long from = (long) (query.getPage() - 1) * pageSize;
        List<Object> items = new ArrayList<>();
        for (long i = from; i < Math.min(total, from + pageSize); i++) {
            items.add(matched.get((int) i).toMap());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", query.getPage());
        body.put("pageSize", pageSize);
        body.put("totalItems", total);
        body.put("totalPages", totalPages);
        return body;
    }

    public List<Task> listByPriority(String level) {
        Priority p = parsePriority(level);
        if (p == null) {
            throw new ValidationException("level", "must be LOW, MEDIUM or HIGH");
        }
        List<Task> result = new ArrayList<>();
        for (Task t : repository.findAll()) {
            if (t.getPriority() == p) {
                result.add(t);
            }
        }
        return result;
    }

    // ---------- Validation ----------

    private static Priority parsePriority(String s) {
        if (s == null) {
            return null;
        }
        switch (s) {
            case "LOW": return Priority.LOW;
            case "MEDIUM": return Priority.MEDIUM;
            case "HIGH": return Priority.HIGH;
            default: return null;
        }
    }

    /**
     * Checks every rule, collecting issues. Throws ValidationException when called with a fresh
     * list and anything is wrong; with a shared list it only records issues (caller decides).
     */
    private Task.Builder validate(TaskRequest r, List<FieldIssue> issues, String prefix) {
        boolean ownList = prefix.isEmpty();
        if (r == null) {
            throw new ValidationException("request", "must not be null");
        }
        if (r.getTitle() == null || r.getTitle().isBlank()) {
            issues.add(new FieldIssue(prefix + "title", "must not be empty"));
        }
        if (r.getDescription() == null || r.getDescription().isBlank()) {
            issues.add(new FieldIssue(prefix + "description", "must not be empty"));
        }
        boolean completed = false;
        if ("true".equals(r.getCompleted())) {
            completed = true;
        } else if (!"false".equals(r.getCompleted())) {
            issues.add(new FieldIssue(prefix + "completed", "must be \"true\" or \"false\""));
        }
        if (r.getDueDate() != null && !DateHelper.isValidDate(r.getDueDate())) {
            issues.add(new FieldIssue(prefix + "dueDate", "must be a real date in YYYY-MM-DD format"));
        }
        Priority priority = Priority.MEDIUM;
        if (r.getPriority() != null) {
            priority = parsePriority(r.getPriority());
            if (priority == null) {
                issues.add(new FieldIssue(prefix + "priority", "must be LOW, MEDIUM or HIGH"));
            }
        }
        if (ownList && !issues.isEmpty()) {
            throw new ValidationException(issues);
        }
        if (!issues.isEmpty()) {
            // shared list: return a dummy builder, the caller will throw
            return new Task.Builder("x", "x", false);
        }
        return new Task.Builder(r.getTitle(), r.getDescription(), completed)
                .dueDate(r.getDueDate()).priority(priority);
    }
}
