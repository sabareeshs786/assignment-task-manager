package service;

import model.Task;

import java.util.Comparator;

/** The built-in SortStrategy implementations. */
public final class Sorts {
    private Sorts() {
    }

    public static class CreatedAtSort implements SortStrategy {
        @Override
        public Comparator<Task> comparator(boolean ascending) {
            // id breaks ties so tasks created in the same millisecond keep creation order
            Comparator<Task> c = Comparator.comparingLong(Task::getCreatedAt).thenComparingInt(Task::getId);
            return ascending ? c : c.reversed();
        }
    }

    /** Tasks without a due date always go last. YYYY-MM-DD strings sort correctly as text. */
    public static class DueDateSort implements SortStrategy {
        @Override
        public Comparator<Task> comparator(boolean ascending) {
            Comparator<String> dates = ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
            return Comparator.comparing(Task::getDueDate, Comparator.nullsLast(dates))
                    .thenComparingInt(Task::getId);
        }
    }
}
