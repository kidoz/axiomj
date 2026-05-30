package su.kidoz.axiomj.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ForAll {
    String value() default "";

    /**
     * A custom generator for this parameter. Must be a class implementing {@code su.kidoz.axiomj.property.Arbitrary}
     * with an accessible no-argument constructor. When left as the default, the engine uses its built-in generators. A
     * custom generator's {@code shrink} is also used to minimize failing samples.
     */
    Class<?> gen() default Object.class;
}
