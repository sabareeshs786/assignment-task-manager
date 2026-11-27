package service;

import model.Task;

/** One filter rule. Add a new class to add a new filter (Open/Closed). */
public interface TaskFilter {
    boolean matches(Task task);
}
