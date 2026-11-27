package parser;

import model.Task;
import model.TaskRequest;

import java.util.List;

/** Strategy interface: propose subtask requests for a task. Throws ApiException on failure. */
public interface BreakdownStrategy {
    List<TaskRequest> breakDown(Task task);
}
