package repository;

import model.Task;

import java.util.List;

public interface TaskRepository {
    /** Stores the task; assigns the next id when task.getId() == 0. Returns the stored task. */
    Task save(Task task);

    /** Returns the task, or null. */
    Task findById(int id);

    List<Task> findAll();

    /** True if something was removed. */
    boolean delete(int id);
}
