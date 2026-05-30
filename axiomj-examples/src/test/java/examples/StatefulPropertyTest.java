package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.util.List;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Property;
import su.kidoz.axiomj.property.Arbitrary;
import su.kidoz.axiomj.property.GenerationContext;
import su.kidoz.axiomj.property.stateful.Action;
import su.kidoz.axiomj.property.stateful.StateMachine;

@ProductArea("Core")
@Feature(id = "property.stateful", name = "Stateful Property Testing")
public class StatefulPropertyTest {

    // The system under test (SUT)
    static class BoundedCounter {
        private int count = 0;
        private final int max;

        BoundedCounter(int max) {
            this.max = max;
        }

        void increment() {
            if (count < max) count++;
        }

        void decrement() {
            if (count > 0) count--;
        }

        int get() {
            return count;
        }
    }

    // The model representing expected behavior
    static class CounterModel {
        int count = 0;
    }

    static final Arbitrary<Action<CounterModel, BoundedCounter>> INCREMENT = context -> new Action<>() {
        @Override
        public void apply(CounterModel model, BoundedCounter sut) {
            if (model.count < 5) model.count++;
            sut.increment();
            expect(sut.get()).isEqualTo(model.count);
        }
    };

    static final Arbitrary<Action<CounterModel, BoundedCounter>> DECREMENT = context -> new Action<>() {
        @Override
        public boolean precondition(CounterModel model) {
            return model.count > 0;
        }

        @Override
        public void apply(CounterModel model, BoundedCounter sut) {
            model.count--;
            sut.decrement();
            expect(sut.get()).isEqualTo(model.count);
        }
    };

    // We can't automatically inject `List<Action>` via @ForAll yet because the
    // engine doesn't know how to resolve Arbitrary bindings natively without explicit manual wiring.
    // However, we can construct the Arbitrary manually to show how it's used.
    @Property(tries = 100)
    void counterAdheresToModel() throws Throwable {
        var seqGen = StateMachine.sequence(1, 20, INCREMENT, DECREMENT);

        // This normally would be injected, but here we generate it manually for the test
        var context = new GenerationContext(new java.util.SplittableRandom(), 0, 0);
        List<Action<CounterModel, BoundedCounter>> actions = seqGen.generate(context);

        var model = new CounterModel();
        var sut = new BoundedCounter(5);

        new StateMachine<>(model, sut).run(actions);
    }
}
