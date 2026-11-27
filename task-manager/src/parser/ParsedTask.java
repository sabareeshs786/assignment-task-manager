package parser;

/** What a TaskParser extracts from free text. Everything is raw text: it is validated later. */
public class ParsedTask {
    private final String title;
    private final String description;
    private final String dueDate;
    private final String recurrence;

    public ParsedTask(String title, String description, String dueDate, String recurrence) {
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.recurrence = recurrence;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getDueDate() { return dueDate; }
    /** Optional, may be null. */
    public String getRecurrence() { return recurrence; }
}
