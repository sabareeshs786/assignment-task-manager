package service;

/** Query options built with the Builder pattern. All optional; raw Strings are validated by the service. */
public class TaskQuery {
    private final String completed;
    private final String priority;
    private final String search;
    private final String sortBy;
    private final String order;
    private final int page;
    private final int pageSize;

    private TaskQuery(Builder b) {
        this.completed = b.completed;
        this.priority = b.priority;
        this.search = b.search;
        this.sortBy = b.sortBy;
        this.order = b.order;
        this.page = b.page;
        this.pageSize = b.pageSize;
    }

    public String getCompleted() { return completed; }
    public String getPriority() { return priority; }
    public String getSearch() { return search; }
    public String getSortBy() { return sortBy; }
    public String getOrder() { return order; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String completed;
        private String priority;
        private String search;
        private String sortBy;
        private String order;
        private int page = 1;
        private int pageSize = 10;

        public Builder completed(String completed) { this.completed = completed; return this; }
        public Builder priority(String priority) { this.priority = priority; return this; }
        public Builder search(String search) { this.search = search; return this; }
        public Builder sortBy(String sortBy) { this.sortBy = sortBy; return this; }
        public Builder order(String order) { this.order = order; return this; }
        public Builder page(int page) { this.page = page; return this; }
        public Builder pageSize(int pageSize) { this.pageSize = pageSize; return this; }

        public TaskQuery build() { return new TaskQuery(this); }
    }
}
