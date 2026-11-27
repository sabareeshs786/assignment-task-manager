package model;

/** Raw input: everything is a String so the service decides what is acceptable. */
public class TaskRequest {
    private final String title;
    private final String description;
    private final String completed;
    private final String dueDate;
    private final String priority;
    private final String recurrence;

    public TaskRequest(String title, String description, String completed, String dueDate, String priority) {
        this(title, description, completed, dueDate, priority, null);
    }

    public TaskRequest(String title, String description, String completed, String dueDate, String priority,
                       String recurrence) {
        this.title = title;
        this.description = description;
        this.completed = completed;
        this.dueDate = dueDate;
        this.priority = priority;
        this.recurrence = recurrence;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCompleted() { return completed; }
    public String getDueDate() { return dueDate; }
    public String getPriority() { return priority; }
    public String getRecurrence() { return recurrence; }
}
