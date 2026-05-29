package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import su.kidoz.axiomj.api.Execution;
import su.kidoz.axiomj.api.ExecutionMode;
import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.Inject;
import su.kidoz.axiomj.api.Order;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Scenario;
import su.kidoz.axiomj.api.UseModules;
import su.kidoz.axiomj.di.Binder;
import su.kidoz.axiomj.di.Scope;
import su.kidoz.axiomj.di.TestModule;

@ProductArea("Testing")
@Feature(id = "di.shared", name = "Shared scope lifetime", owner = "core-team")
@Execution(ExecutionMode.SEQUENTIAL)
@UseModules(SharedScopeTest.Module.class)
public final class SharedScopeTest {

    @Inject
    Counter counter;

    @Fact(name = "shared counter, first")
    @Order(1)
    @Scenario("a SHARED service is created once and reused across tests in the class")
    void first() {
        expect(counter.next()).isEqualTo(1);
    }

    @Fact(name = "shared counter, second")
    @Order(2)
    @Scenario("the second test sees the same shared instance")
    void second() {
        expect(counter.next()).isEqualTo(2);
    }

    public static final class Counter implements AutoCloseable {
        private int value;

        int next() {
            return ++value;
        }

        @Override
        public void close() {}
    }

    public static final class Module implements TestModule {
        @Override
        public void configure(Binder binder) {
            binder.bind(Counter.class, Counter::new, Scope.SHARED);
        }
    }
}
