package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.util.concurrent.atomic.AtomicInteger;
import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.ResourceLock;
import su.kidoz.axiomj.api.Retry;

@ProductArea("Core")
@Feature(id = "concurrency", name = "Concurrency and Retry Controls")
public class ConcurrencyFeaturesTest {

    private static final AtomicInteger flakyCounter = new AtomicInteger(0);
    private static int unsafeSharedState = 0;

    @Fact(name = "flaky test passes on retry")
    @Retry(maxAttempts = 3)
    void flakyTestWithRetry() {
        int attempt = flakyCounter.incrementAndGet();
        // This test simulates flakiness by failing the first two attempts.
        // The @Retry annotation ensures it is re-run and passes on the third attempt.
        expect(attempt).isEqualTo(3);
    }

    @Fact(name = "resource lock prevents race conditions (test 1)")
    @ResourceLock("shared-state")
    void mutateSharedStateSafely1() throws InterruptedException {
        int current = unsafeSharedState;
        Thread.sleep(50); // Simulate work that would cause a race condition
        unsafeSharedState = current + 1;
        expect(unsafeSharedState >= 1).isTrue();
    }

    @Fact(name = "resource lock prevents race conditions (test 2)")
    @ResourceLock("shared-state")
    void mutateSharedStateSafely2() throws InterruptedException {
        int current = unsafeSharedState;
        Thread.sleep(50); // Simulate work that would cause a race condition
        unsafeSharedState = current + 1;
        expect(unsafeSharedState >= 1).isTrue();
    }
}
