package app;

import api.ApiResponse;
import api.TaskApi;
import event.AuditTrailListener;
import event.ConsoleLogListener;
import model.TaskRequest;
import parser.ParserFactory;
import repository.IdempotencyStore;
import repository.InMemoryTaskRepository;
import service.TaskQuery;
import service.TaskService;

/** Demo of every operation. Uses the real model if LLM_API_KEY is set, else the offline rule-based strategies. */
public class Main {
    public static void main(String[] args) {
        System.out.println("Task Manager started");

        InMemoryTaskRepository repo = InMemoryTaskRepository.getInstance();
        TaskService service = new TaskService(repo,
                ParserFactory.createParser(true), ParserFactory.createBreakdown(true));
        TaskApi api = new TaskApi(service);

        ConsoleLogListener log = new ConsoleLogListener();
        AuditTrailListener audit = new AuditTrailListener();
        service.addListener(log);
        service.addListener(audit);

        section("Step 2: CRUD");
        show(api.createTask(new TaskRequest("Pay rent", "Monthly rent", "false", "2026-12-01", "HIGH"), null));
        show(api.createTask(new TaskRequest("Buy milk", "Two litres", "false", "2026-11-30", "LOW"), null));
        show(api.getTask(1));
        show(api.getTask(99));
        show(api.updateTask(2, new TaskRequest("Buy milk", "Two litres", "true", "2026-11-30", "LOW")));
        show(api.listTasks(null));

        section("Step 3: validation (several issues at once)");
        show(api.createTask(new TaskRequest("", " ", "maybe", "2026-02-30", "URGENT"), null));

        section("Step 4: AI / rule-based creation");
        show(api.createTaskFromText("Remind me to pay rent on the 1st of every month", null));

        section("Step 5: subtasks, breakdown, cascade delete");
        show(api.createTask(new TaskRequest("Plan trip: book flights and pack bags", "Holiday", "false", null, "MEDIUM"), null));
        int parent = 4;
        show(api.createSubtask(parent, new TaskRequest("Pick dates", "Check calendar", "false", null, null)));
        show(api.breakDownTask(parent, null));
        show(api.listSubtasks(parent));
        show(api.createSubtask(5, new TaskRequest("Nested", "Not allowed", "false", null, null)));
        show(api.breakDownTask(5, null));
        System.out.println("-- repository BEFORE delete:");
        printRepo(repo);
        show(api.deleteTask(parent));
        System.out.println("-- repository AFTER delete:");
        printRepo(repo);

        section("Step 6: idempotency");
        TaskRequest rent = new TaskRequest("Water plants", "Weekly", "false", null, "LOW");
        show(api.createTask(rent, "key-1"));
        show(api.createTask(rent, "key-1"));
        show(api.createTask(new TaskRequest("Other", "Different", "false", null, "LOW"), "key-1"));
        System.out.println("-- repository (only one 'Water plants'):");
        printRepo(repo);

        section("Step 7: filter + search + sort + paginate in one call");
        api.createTask(new TaskRequest("Write report", "quarterly report", "false", "2026-12-15", "HIGH"), null);
        api.createTask(new TaskRequest("Read report", "review the REPORT", "false", "2026-12-05", "HIGH"), null);
        api.createTask(new TaskRequest("Report bug", "ticket", "true", "2026-12-01", "HIGH"), null);
        show(api.listTasks(TaskQuery.builder().completed("false").priority("HIGH").search("report")
                .sortBy("dueDate").order("asc").page(1).pageSize(1).build()));
        show(api.listTasks(TaskQuery.builder().priority("HIGH").page(50).build()));
        show(api.listByPriority("LOW"));
        show(api.listByPriority("URGENT"));

        section("Step 8: observer");
        System.out.println("Audit counts: " + audit.getCounts());
        service.removeListener(log);
        System.out.println("(ConsoleLogListener removed - no [LOG] line should follow)");
        show(api.createTask(new TaskRequest("Silent task", "No log line", "false", null, null), null));
        System.out.println("Audit counts: " + audit.getCounts());

        IdempotencyStore.getInstance().clear();
    }

    private static void section(String name) {
        System.out.println("\n===== " + name + " =====");
    }

    private static void show(ApiResponse r) {
        System.out.println(r.toJson());
    }

    private static void printRepo(InMemoryTaskRepository repo) {
        repo.findAll().forEach(t -> System.out.println("   " + t));
    }
}
