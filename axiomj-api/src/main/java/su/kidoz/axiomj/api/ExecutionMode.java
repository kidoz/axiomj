package su.kidoz.axiomj.api;

public enum ExecutionMode {
    CONCURRENT,
    SEQUENTIAL,
    /** Alias for {@link #SEQUENTIAL}, for teams that prefer same-thread wording. */
    SAME_THREAD
}
