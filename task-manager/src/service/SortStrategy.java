package service;

import model.Task;

import java.util.Comparator;

/** One sort order. Add a new class to add a new sort (Open/Closed). */
public interface SortStrategy {
    Comparator<Task> comparator(boolean ascending);
}
