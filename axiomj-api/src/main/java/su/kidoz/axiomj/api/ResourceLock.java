package su.kidoz.axiomj.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the annotated test method or all test methods within the annotated class require exclusive access to a
 * named resource. If multiple running tests require the same lock name, they will be forced to execute sequentially
 * relative to each other, while still remaining concurrent with tests that do not share the lock.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ResourceLock {
    /** The name of the resource to lock (e.g., "database", "redis"). */
    String value();
}
