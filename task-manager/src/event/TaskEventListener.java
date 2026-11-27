package event;

import model.Task;

/** Observer interface. */
public interface TaskEventListener {
    String CREATED = "TASK_CREATED";
    String UPDATED = "TASK_UPDATED";
    String COMPLETED = "TASK_COMPLETED";
    String DELETED = "TASK_DELETED";

    void onEvent(String eventType, Task task);
}
