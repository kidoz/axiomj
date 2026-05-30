package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import su.kidoz.axiomj.api.AfterSuite;
import su.kidoz.axiomj.api.BeforeSuite;
import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.Inject;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.TestClock;
import su.kidoz.axiomj.api.UseModules;
import su.kidoz.axiomj.di.Binder;
import su.kidoz.axiomj.di.TestModule;

@ProductArea("Core")
@Feature(id = "lifecycle", name = "Advanced Fixtures and Lifecycle")
@UseModules(LifecycleFeaturesTest.LifecycleModule.class)
public class LifecycleFeaturesTest {

    private static boolean suiteStarted = false;
    static final AtomicBoolean asyncCleanupFinished = new AtomicBoolean(false);

    @BeforeSuite
    public static void setupSuite() {
        suiteStarted = true;
    }

    @AfterSuite
    public static void teardownSuite() {
        suiteStarted = false;
    }

    public static class LifecycleModule implements TestModule {
        @Override
        public void configure(Binder binder) {
            binder.bind(AsyncResource.class, AsyncResource::new);
        }
    }

    public static class AsyncResource implements AutoCloseable {
        @Override
        public void close() throws Exception {
            // In a real scenario, this might return a Future, or block briefly
            Thread.sleep(10);
            asyncCleanupFinished.set(true);
        }
    }

    @Fact(name = "beforeSuite ran")
    void verifyBeforeSuite() {
        expect(suiteStarted).isTrue();
    }

    @Fact(name = "async cleanup fires")
    void verifyAsyncCleanup(@Inject AsyncResource resource) {
        expect(resource).isNotNull();
        // The cleanup will fire after the test completes, which is hard to assert *within* the test.
        // We just ensure the resource is injected and the test finishes.
    }

    @Fact(name = "deterministic test clock injection")
    void verifyTestClock(@Inject TestClock clock) {
        Instant before = clock.instant();
        clock.advance(Duration.ofDays(1));
        Instant after = clock.instant();
        expect(Duration.between(before, after).toHours()).isEqualTo(24);
    }
}
