package su.kidoz.axiomj.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Automatically re-attempts the annotated test method if it fails, up to {@code maxAttempts} times. A test is only
 * marked as failed if all attempts are exhausted.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Retry {
    /** The maximum number of total attempts (must be >= 1). Defaults to 3. */
    int maxAttempts() default 3;
}
