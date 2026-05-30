package su.kidoz.axiomj.property.stateful;

import java.util.ArrayList;
import java.util.List;
import su.kidoz.axiomj.property.Arbitrary;
import su.kidoz.axiomj.property.GenerationContext;

public final class StateMachine<M, S> {

    private final M initialModel;
    private final S sut;

    public StateMachine(M initialModel, S sut) {
        this.initialModel = initialModel;
        this.sut = sut;
    }

    public void run(List<Action<M, S>> actions) throws Throwable {
        for (Action<M, S> action : actions) {
            if (action.precondition(initialModel)) {
                action.apply(initialModel, sut);
            }
        }
    }

    @SafeVarargs
    public static <M, S> Arbitrary<List<Action<M, S>>> sequence(
            int minLength, int maxLength, Arbitrary<Action<M, S>>... actions) {
        return new Arbitrary<List<Action<M, S>>>() {
            @Override
            public List<Action<M, S>> generate(GenerationContext context) {
                if (actions.length == 0) return List.of();
                int length = context.random().nextInt(minLength, maxLength + 1);
                List<Action<M, S>> sequence = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    Arbitrary<Action<M, S>> generator = actions[context.random().nextInt(actions.length)];
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
