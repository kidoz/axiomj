package su.kidoz.axiomj.di;

public enum Scope {
    /** A new instance is created on every resolution. */
    TRANSIENT,
    /** One instance per test invocation (the default for {@code bind}). */
    PER_TEST,
    /** One instance shared across every test in the class. */
    SHARED,
    /** Alias for {@link #SHARED}; also used for fixed instances bound via {@code bindInstance}. */
    SINGLETON
}
