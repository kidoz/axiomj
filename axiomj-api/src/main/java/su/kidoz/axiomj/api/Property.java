package su.kidoz.axiomj.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Property {
    String name() default "";

    String[] tags() default {};

    String[] dependsOn() default {};

    int tries() default 100;

    long seed() default Long.MIN_VALUE;

    long timeoutMillis() default 0;
}
