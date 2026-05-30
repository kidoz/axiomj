package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.util.List;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.ForAll;
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

    /**
     * A named {@link Arbitrary} so it can be referenced from {@code @ForAll(gen = ...)}; it just delegates to
     * {@link StateMachine#sequence} for both generation and shrinking.
     */
    public static final class CounterActions implements Arbitrary<List<Action<CounterModel, BoundedCounter>>> {
        private final Arbitrary<List<Action<CounterModel, BoundedCounter>>> delegate =
                StateMachine.sequence(1, 20, INCREMENT, DECREMENT);

        @Override
        public List<Action<CounterModel, BoundedCounter>> generate(GenerationContext context) {
            return delegate.generate(context);
        }

        @Override
        public List<List<Action<CounterModel, BoundedCounter>>> shrink(List<Action<CounterModel, BoundedCounter>> v) {
            return delegate.shrink(v);
        }
    }

    // The action sequence is now generated and injected natively via @ForAll(gen = ...), and a failing sequence is
    // shrunk through the custom Arbitrary's shrink(). A fresh model + SUT is built per attempt, so runs stay isolated.
    @Property(tries = 100)
    void counterAdheresToModel(@ForAll(gen = CounterActions.class) List<Action<CounterModel, BoundedCounter>> actions)
            throws Throwable {
        var model = new CounterModel();
        var sut = new BoundedCounter(5);
        new StateMachine<>(model, sut).run(actions);
    }
}
