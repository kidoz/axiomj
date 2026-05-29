package su.kidoz.axiomj.mock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Argument matchers for {@link Mocks#when} and {@link Mocks#verify}.
 *
 * <p>Each matcher call pushes an {@link ArgMatcher} onto a thread-local stack while a capture is active and returns a
 * type-correct placeholder, so it can be used inline as a method argument: {@code when(() ->
 * repo.find(Arg.any())).thenReturn(...)}.
 *
 * <p>Within a single mocked call you must use matchers for either all arguments or none. When any matcher is used, wrap
 * literal values with {@link #eq(Object)}.
 */
public final class Arg {
    private Arg() {}

    private static final ThreadLocal<Deque<ArgMatcher>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private static void push(ArgMatcher matcher) {
        STACK.get().addLast(matcher);
    }

    /** Drains the matchers pushed during the current capture, in argument order. */
    static List<ArgMatcher> drain() {
        var stack = STACK.get();
        var drained = new ArrayList<>(stack);
        stack.clear();
        return drained;
    }

    /** Clears any matchers left over from an aborted capture. */
    static void reset() {
        STACK.get().clear();
    }

    /** Matches any value, including {@code null}. */
    public static <T> T any() {
        push(actual -> true);
        return null;
    }

    /** Matches any non-null instance of {@code type}. */
    public static <T> T isA(Class<T> type) {
        push(type::isInstance);
        return null;
    }

    /** Matches values equal to {@code value} (returns the value so it type-checks inline). */
    public static <T> T eq(T value) {
        push(actual -> Objects.equals(actual, value));
        return value;
    }

    /** Matches values satisfying {@code predicate}. */
    public static <T> T matches(Predicate<? super T> predicate) {
        push(actual -> {
            @SuppressWarnings("unchecked")
            T typed = (T) actual;
            return predicate.test(typed);
        });
        return null;
    }

    /** Matches any non-null value. */
    public static <T> T notNull() {
        push(Objects::nonNull);
        return null;
    }

    /** Matches only {@code null}. */
    public static <T> T isNull() {
        push(Objects::isNull);
        return null;
    }

    /** Matches any value and records it into {@code captor}; use only in {@code verify}. */
    public static <T> T capture(ArgumentCaptor<T> captor) {
        push(captor.asMatcher());
        return null;
    }

    public static int anyInt() {
        push(actual -> actual instanceof Integer);
        return 0;
    }

    public static long anyLong() {
        push(actual -> actual instanceof Long);
        return 0L;
    }

    public static boolean anyBoolean() {
        push(actual -> actual instanceof Boolean);
        return false;
    }

    public static double anyDouble() {
        push(actual -> actual instanceof Double);
        return 0d;
    }
}
