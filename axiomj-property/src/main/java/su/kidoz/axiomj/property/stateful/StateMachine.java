package su.kidoz.axiomj.property.stateful;

import java.util.ArrayList;
import java.util.List;
import su.kidoz.axiomj.property.Arbitrary;
import su.kidoz.axiomj.property.GenerationContext;

public final class StateMachine<M, S> {

    private final M model;
    private final S sut;

    /**
     * Creates a single-use state machine over a fresh model and system under test. The model is expected to be mutated
     * in place by {@link Action#apply}. Construct a new instance (with fresh {@code model}/{@code sut}) for every
     * generated sequence — reusing an instance would carry mutated state across runs and make results non-reproducible.
     * When driven from a {@code @Property} via {@code @ForAll}, the engine already constructs fresh arguments per
     * attempt, so building the model/SUT in the test body satisfies this.
     */
    public StateMachine(M model, S sut) {
        this.model = model;
        this.sut = sut;
    }

    /** Applies each action whose precondition holds against the (in-place mutated) model, asserting SUT parity. */
    public void run(List<Action<M, S>> actions) throws Throwable {
        for (Action<M, S> action : actions) {
            if (action.precondition(model)) {
                action.apply(model, sut);
            }
        }
    }

    @SafeVarargs
    public static <M, S> Arbitrary<List<Action<M, S>>> sequence(
            int minLength, int maxLength, Arbitrary<Action<M, S>>... actions) {
        return new Arbitrary<>() {
            @Override
            public List<Action<M, S>> generate(GenerationContext context) {
                if (actions.length == 0) return List.of();
                int length = context.random().nextInt(minLength, maxLength + 1);
                var sequence = new ArrayList<Action<M, S>>(length);
                for (int i = 0; i < length; i++) {
                    var generator = actions[context.random().nextInt(actions.length)];
                    sequence.add(generator.generate(context));
                }
                return sequence;
            }

            @Override
            public List<List<Action<M, S>>> shrink(List<Action<M, S>> value) {
                if (value.isEmpty()) return List.of();
                var out = new ArrayList<List<Action<M, S>>>();
                out.add(List.of());
                if (value.size() > 1) {
                    out.add(new ArrayList<>(value.subList(0, value.size() / 2)));
                    out.add(new ArrayList<>(value.subList(0, value.size() - 1)));
                }
                return out;
            }
        };
    }
}
