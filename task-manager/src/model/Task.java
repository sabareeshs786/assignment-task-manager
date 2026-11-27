package model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable task. Created only through the Builder. */
public final class Task {
    private final int id;
    private final String title;
    private final String description;
    private final boolean completed;
    private final String dueDate;
    private final Priority priority;
    private final Integer parentId;
    private final long createdAt;
    private final String recurrence;

    private Task(Builder b) {
        this.id = b.id;
        this.title = b.title;
        this.description = b.description;
        this.completed = b.completed;
        this.dueDate = b.dueDate;
        this.priority = b.priority;
        this.parentId = b.parentId;
        this.createdAt = b.createdAt;
        this.recurrence = b.recurrence;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public boolean isCompleted() { return completed; }
    public String getDueDate() { return dueDate; }
    public Priority getPriority() { return priority; }
    public Integer getParentId() { return parentId; }
    public long getCreatedAt() { return createdAt; }
    public String getRecurrence() { return recurrence; }

    /** Builder pre-filled with this task's values (to make a modified copy). */
    public Builder toBuilder() {
        return new Builder(title, description, completed)
                .id(id).dueDate(dueDate).priority(priority).parentId(parentId).createdAt(createdAt)
                .recurrence(recurrence);
    }

    /** Keeps JSON out of the model: plain Map for ApiResponse. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("description", description);
        m.put("completed", completed);
        m.put("dueDate", dueDate);
        m.put("priority", priority.name());
        m.put("parentId", parentId);
        m.put("createdAt", createdAt);
        m.put("recurrence", recurrence);
        return m;
    }

    @Override
    public String toString() {
        return "Task{id=" + id + ", title='" + title + "', completed=" + completed
                + ", dueDate=" + dueDate + ", priority=" + priority + ", parentId=" + parentId + "}";
    }

    public static class Builder {
        private final String title;
        private final String description;
        private final boolean completed;
        private int id;
        private String dueDate;
        private Priority priority = Priority.MEDIUM;
        private Integer parentId;
        private long createdAt = System.currentTimeMillis();
        private String recurrence;

        /** The three required fields. */
        public Builder(String title, String description, boolean completed) {
            if (title == null || description == null) {
                throw new IllegalArgumentException("title and description are required");
            }
            this.title = title;
            this.description = description;
            this.completed = completed;
        }

        public Builder id(int id) { this.id = id; return this; }
        public Builder dueDate(String dueDate) { this.dueDate = dueDate; return this; }
        public Builder priority(Priority priority) { this.priority = priority == null ? Priority.MEDIUM : priority; return this; }
        public Builder parentId(Integer parentId) { this.parentId = parentId; return this; }
        public Builder createdAt(long createdAt) { this.createdAt = createdAt; return this; }
        public Builder recurrence(String recurrence) { this.recurrence = recurrence; return this; }

        public Task build() { return new Task(this); }
    }
}
