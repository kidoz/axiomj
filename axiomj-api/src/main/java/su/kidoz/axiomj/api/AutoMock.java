package su.kidoz.axiomj.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables auto-mocking for a test class: any interface dependency that is not bound in a {@code TestModule} is resolved
 * to a lenient mock instead of failing. The same mock instance is shared between the system under test and any matching
 * {@code @Mock} field/parameter, so it can be stubbed and verified.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AutoMock {}
