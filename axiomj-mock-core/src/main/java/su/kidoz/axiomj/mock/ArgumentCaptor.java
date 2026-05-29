package su.kidoz.axiomj.mock;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures arguments passed to a mock so they can be asserted after verification.
 *
 * <pre>{@code
 * var id = ArgumentCaptor.forClass(String.class);
 * Mocks.verify(() -> repo.find(Arg.capture(id)));
 * expect(id.value()).isEqualTo("42");
 * }</pre>
 */
public final class ArgumentCaptor<T> {
    private final List<T> captured = new ArrayList<>();

    private ArgumentCaptor() {}

    public static <T> ArgumentCaptor<T> forClass(Class<T> type) {
        return new ArgumentCaptor<>();
    }

    public static <T> ArgumentCaptor<T> create() {
        return new ArgumentCaptor<>();
    }

    ArgMatcher asMatcher() {
        return actual -> {
            @SuppressWarnings("unchecked")
            T typed = (T) actual;
            captured.add(typed);
            return true;
        };
    }

    /** The most recently captured value. */
    public T value() {
        if (captured.isEmpty()) {
            throw new IllegalStateException("No argument was captured");
        }
        return captured.get(captured.size() - 1);
    }

    /** All captured values, in invocation order. */
    public List<T> values() {
        return List.copyOf(captured);
    }
}
