package su.kidoz.axiomj.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Loads one or more {@code .properties} resources (from the test classpath) into a {@code Config} that is injectable
 * and backs {@link Value} resolution for the annotated test class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UseConfig {
    String[] value();
}
