package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;
import static su.kidoz.axiomj.assertions.Expect.expectThrown;

import java.time.Duration;
import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.Mock;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Scenario;
import su.kidoz.axiomj.assertions.AssertionFailed;
import su.kidoz.axiomj.assertions.Expect;
import su.kidoz.axiomj.mock.Mocks;

@ProductArea("Testing")
@Feature(id = "engine.guardrails", name = "Framework guardrails", owner = "core-team")
public final class GuardrailsTest {

    interface Service {
        void ping();
    }

    @Fact(name = "completesWithin actually bounds a runaway action")
    @Scenario("a never-ending action is interrupted at the budget instead of hanging the test")
    void completesWithinBoundsRunaway() {
        long startNanos = System.nanoTime();
        expectThrown(() -> Expect.completesWithin(Duration.ofMillis(100), () -> Thread.sleep(10_000)))
                .isInstanceOf(AssertionFailed.class);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        // It must not have waited the full 10s — the action is interrupted at the budget.
        expect(elapsedMillis < 5_000).isTrue();
    }

    @Fact(name = "inOrder rejects a mock it was not given")
    @Scenario("verifying a call on a mock not passed to inOrder(...) is an error, not silently ignored")
    void inOrderScopesToItsMocks(@Mock Service a, @Mock Service b) {
        a.ping();
        b.ping();

        var order = Mocks.inOrder(a); // only `a` is in scope
        order.verify(() -> a.ping());

        expectThrown(() -> order.verify(() -> b.ping())).isInstanceOf(IllegalArgumentException.class);
    }
}
