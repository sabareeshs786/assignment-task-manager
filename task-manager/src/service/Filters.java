package service;

import model.Priority;
import model.Task;

/** The built-in TaskFilter implementations. */
public final class Filters {
    private Filters() {
    }

    public static class CompletedFilter implements TaskFilter {
        private final boolean completed;

        public CompletedFilter(boolean completed) { this.completed = completed; }

        @Override
        public boolean matches(Task t) { return t.isCompleted() == completed; }
    }

    public static class PriorityFilter implements TaskFilter {
        private final Priority priority;

        public PriorityFilter(Priority priority) { this.priority = priority; }

        @Override
        public boolean matches(Task t) { return t.getPriority() == priority; }
    }

    public static class SearchFilter implements TaskFilter {
        private final String needle;

        public SearchFilter(String text) { this.needle = text.toLowerCase(); }

        @Override
        public boolean matches(Task t) {
            return t.getTitle().toLowerCase().contains(needle)
                    || t.getDescription().toLowerCase().contains(needle);
        }
    }
}
