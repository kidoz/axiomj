package su.kidoz.axiomj.property.stateful;

/**
 * Represents a state transition in model-based property testing.
 *
 * @param <M> The type of the abstract model (expected state).
 * @param <S> The type of the concrete System Under Test (SUT).
 */
public interface Action<M, S> {

    /** @return true if this action is valid in the current model state. */
    default boolean precondition(M model) {
        return true;
    }

    /** Applies the action, mutating the model and the SUT, and asserting they behave identically. */
    void apply(M model, S sut) throws Throwable;

    /** @return A human-readable description of this action for reporting. */
    default String name() {
        return getClass().getSimpleName();
    }
}
