package su.kidoz.axiomj.engine;

import static su.kidoz.axiomj.assertions.Expect.expect;
import static su.kidoz.axiomj.assertions.Expect.expectThrown;

import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.di.SimpleContainer;

/** Dogfood: AxiomJ tests its own DI container — resolution and cycle detection. */
class ContainerTest {

    static final class Service {
        String hello() {
            return "hi";
        }
    }

    // A depends on B which depends on A: an unbreakable constructor cycle.
    static final class A {
        A(B b) {}
    }

    static final class B {
        B(A a) {}
    }

    @Fact(name = "container resolves a bound instance")
    void resolvesBoundInstance() {
        var container = new SimpleContainer();
        container.bindInstance(String.class, "hello");
        expect(container.get(String.class)).isEqualTo("hello");
    }

    @Fact(name = "container auto-constructs a concrete type")
    void autoConstructsConcreteType() {
        var container = new SimpleContainer();
        expect(container.get(Service.class).hello()).isEqualTo("hi");
    }

    @Fact(name = "container detects a dependency cycle with a clear path")
    void detectsDependencyCycle() {
        var container = new SimpleContainer();
        var error = expectThrown(() -> container.construct(A.class));
        error.isInstanceOf(IllegalStateException.class);
        error.hasMessageContaining("Dependency cycle");
    }
}
