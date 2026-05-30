package su.kidoz.axiomj.assertions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

public final class Expect {
    private Expect() {}

    private static final ScopedValue<List<AssertionFailed>> SOFT = ScopedValue.newInstance();

    public static <T> Subject<T> expect(T actual) {
        return new Subject<>(actual);
    }

    public static StringSubject expect(String actual) {
        return new StringSubject(actual);
    }

    public static <T> OptionalSubject<T> expect(Optional<T> actual) {
        return new OptionalSubject<>(actual);
    }

    public static <T> IterableSubject<T> expect(Iterable<T> actual) {
        return new IterableSubject<>(actual);
    }

    public static <K, V> MapSubject<K, V> expect(Map<K, V> actual) {
        return new MapSubject<>(actual);
    }

    public static IntSubject expect(int actual) {
        return new IntSubject(actual);
    }

    public static LongSubject expect(long actual) {
        return new LongSubject(actual);
    }

    public static DoubleSubject expect(double actual) {
        return new DoubleSubject(actual);
    }

    public static BooleanSubject expect(boolean actual) {
        return new BooleanSubject(actual);
    }

    public static ThrowableSubject expectThrown(ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            return new ThrowableSubject(throwable);
        }
        collect(new AssertionFailed("Expected an exception, but nothing was thrown"));
        return new ThrowableSubject(null);
    }

    public static void fail(String message) {
        collect(new AssertionFailed(message));
    }

    /** Asserts that {@code action} completes without throwing. */
    public static void doesNotThrow(ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            collect(new AssertionFailed(
                    "Expected no exception, but " + throwable.getClass().getName() + " was thrown", throwable));
        }
    }

    /**
     * Asserts that {@code action} completes within {@code budget} (and without throwing). The action runs on a separate
     * virtual thread so a runaway action is actually bounded: if it does not finish in time it is interrupted and the
     * assertion fails, rather than hanging the test. Any in-scope {@link #softly} collection is propagated to the
     * worker.
     */
    public static void completesWithin(Duration budget, ThrowingRunnable action) {
        var soft = SOFT.isBound() ? SOFT.get() : null;
        var thrown = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Runnable body = () -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                thrown.set(throwable);
            }
        };
        Runnable scoped =
                soft == null ? body : () -> ScopedValue.where(SOFT, soft).run(body);
        var worker = Thread.ofVirtual().name("axiomj-completes-within").unstarted(scoped);

        long startNanos = System.nanoTime();
        worker.start();
        try {
            worker.join(budget);
        } catch (InterruptedException e) {
            worker.interrupt();
            Thread.currentThread().interrupt();
            return;
        }
        if (worker.isAlive()) {
            worker.interrupt();
            collect(new AssertionFailed(
                    "Expected to complete within %d ms but did not finish".formatted(budget.toMillis())));
            return;
        }
        Throwable failure = thrown.get();
        if (failure != null) {
            collect(new AssertionFailed(
                    "Expected to complete within %d ms but threw %s"
                            .formatted(budget.toMillis(), failure.getClass().getName()),
                    failure));
            return;
        }
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        if (elapsedMillis > budget.toMillis()) {
            collect(new AssertionFailed(
                    "Expected to complete within %d ms but took %d ms".formatted(budget.toMillis(), elapsedMillis)));
        }
    }

    /**
     * Runs {@code assertions} as a soft-assertion scope: failures are collected instead of thrown, and reported
     * together at the end. If any assertion failed, a single aggregated {@link AssertionFailed} is thrown.
     */
    public static void softly(Runnable assertions) {
        var scope = new ArrayList<AssertionFailed>();
        ScopedValue.where(SOFT, scope).run(assertions);
        if (!scope.isEmpty()) {
            var message = new StringBuilder("Soft assertions failed (" + scope.size() + "):");
            int index = 1;
            for (var failure : scope) {
                message.append("\n  ").append(index++).append(") ").append(failure.getMessage());
            }
            throw new AssertionFailed(message.toString());
        }
    }

    private static void collect(AssertionFailed failure) {
        if (SOFT.isBound()) {
            SOFT.get().add(failure);
        } else {
            throw failure;
        }
    }

    public interface ThrowingRunnable {
        void run() throws Throwable;
    }

    /** Base for all subjects: carries an optional {@code as}/{@code because} description and the failure sink. */
    public abstract static sealed class AbstractSubject<S extends AbstractSubject<S>>
            permits Subject,
                    IntSubject,
                    LongSubject,
                    DoubleSubject,
                    BooleanSubject,
                    StringSubject,
                    OptionalSubject,
                    IterableSubject,
                    MapSubject,
                    ThrowableSubject {
        private String description;

        @SuppressWarnings("unchecked")
        final S self() {
            return (S) this;
        }

        /** Attaches a description shown in any failure message from this subject. */
        public final S as(String description) {
            this.description = description;
            return self();
        }

        /** Alias for {@link #as} reading naturally as a reason. */
        public final S because(String reason) {
            return as(reason);
        }

        protected final void report(String message) {
            collect(new AssertionFailed(describe(message)));
        }

        protected final void report(String message, Throwable cause) {
            collect(new AssertionFailed(describe(message), cause));
        }

        private String describe(String message) {
            return description == null || description.isBlank() ? message : "[" + description + "] " + message;
        }
    }

    public static final class Subject<T> extends AbstractSubject<Subject<T>> {
        private final T actual;
        private final Set<String> ignoredFields = new HashSet<>();

        Subject(T actual) {
            this.actual = actual;
        }

        /** Excludes the named fields (at any depth) from {@link #isEquivalentTo}. */
        public Subject<T> ignoringFields(String... names) {
            ignoredFields.addAll(Arrays.asList(names));
            return self();
        }

        /** Deep structural comparison: equal field-by-field (recursing collections/maps/POJOs), ignoring identity. */
        public Subject<T> isEquivalentTo(Object expected) {
            var difference = Equivalence.diff(actual, expected, ignoredFields);
            if (difference != null) {
                report("Expected equivalent object but " + difference);
            }
            return self();
        }

        public Subject<T> isEqualTo(Object expected) {
            if (!Objects.equals(actual, expected)) {
                report("Expected <%s> but was <%s>".formatted(expected, actual));
            }
            return self();
        }

        public Subject<T> isNotEqualTo(Object unexpected) {
            if (Objects.equals(actual, unexpected)) {
                report("Expected value not to equal <%s>".formatted(unexpected));
            }
            return self();
        }

        public Subject<T> isNull() {
            if (actual != null) {
                report("Expected null but was <%s>".formatted(actual));
            }
            return self();
        }

        public Subject<T> isNotNull() {
            if (actual == null) {
                report("Expected a non-null value");
            }
            return self();
        }

        public Subject<T> isGreaterThan(T other) {
            return compareTo(other, cmp -> cmp > 0, "greater than");
        }

        public Subject<T> isGreaterThanOrEqualTo(T other) {
            return compareTo(other, cmp -> cmp >= 0, ">=");
        }

        public Subject<T> isLessThan(T other) {
            return compareTo(other, cmp -> cmp < 0, "less than");
        }

        public Subject<T> isLessThanOrEqualTo(T other) {
            return compareTo(other, cmp -> cmp <= 0, "<=");
        }

        @SuppressWarnings("unchecked")
        private Subject<T> compareTo(T other, IntPredicate accept, String description) {
            if (!(actual instanceof Comparable)) {
                report("Expected a Comparable value but was <%s>".formatted(actual));
                return self();
            }
            if (!accept.test(((Comparable<T>) actual).compareTo(other))) {
                report("Expected <%s> to be %s <%s>".formatted(actual, description, other));
            }
            return self();
        }

        public T actual() {
            return actual;
        }
    }

    public static final class IntSubject extends AbstractSubject<IntSubject> {
        private final int actual;

        IntSubject(int actual) {
            this.actual = actual;
        }

        public IntSubject isEqualTo(int expected) {
            if (actual != expected) {
                report("Expected <%d> but was <%d>".formatted(expected, actual));
            }
            return self();
        }

        public IntSubject isBetween(int minInclusive, int maxInclusive) {
            if (actual < minInclusive || actual > maxInclusive) {
                report("Expected <%d> to be between <%d> and <%d>".formatted(actual, minInclusive, maxInclusive));
            }
            return self();
        }

        public IntSubject isGreaterThan(int other) {
            if (actual <= other) {
                report("Expected <%d> to be greater than <%d>".formatted(actual, other));
            }
            return self();
        }

        public IntSubject isGreaterThanOrEqualTo(int other) {
            if (actual < other) {
                report("Expected <%d> to be >= <%d>".formatted(actual, other));
            }
            return self();
        }

        public IntSubject isLessThan(int other) {
            if (actual >= other) {
                report("Expected <%d> to be less than <%d>".formatted(actual, other));
            }
            return self();
        }

        public IntSubject isLessThanOrEqualTo(int other) {
            if (actual > other) {
                report("Expected <%d> to be <= <%d>".formatted(actual, other));
            }
            return self();
        }
    }

    public static final class LongSubject extends AbstractSubject<LongSubject> {
        private final long actual;

        LongSubject(long actual) {
            this.actual = actual;
        }

        public LongSubject isEqualTo(long expected) {
            if (actual != expected) {
                report("Expected <%d> but was <%d>".formatted(expected, actual));
            }
            return self();
        }

        public LongSubject isGreaterThan(long other) {
            if (actual <= other) {
                report("Expected <%d> to be greater than <%d>".formatted(actual, other));
            }
            return self();
        }

        public LongSubject isGreaterThanOrEqualTo(long other) {
            if (actual < other) {
                report("Expected <%d> to be >= <%d>".formatted(actual, other));
            }
            return self();
        }

        public LongSubject isLessThan(long other) {
            if (actual >= other) {
                report("Expected <%d> to be less than <%d>".formatted(actual, other));
            }
            return self();
        }

        public LongSubject isLessThanOrEqualTo(long other) {
            if (actual > other) {
                report("Expected <%d> to be <= <%d>".formatted(actual, other));
            }
            return self();
        }
    }

    public static final class DoubleSubject extends AbstractSubject<DoubleSubject> {
        private final double actual;

        DoubleSubject(double actual) {
            this.actual = actual;
        }

        /** Asserts the value is within {@code tolerance} of {@code expected}. */
        public DoubleSubject isCloseTo(double expected, double tolerance) {
            if (Double.isNaN(actual) || Math.abs(actual - expected) > tolerance) {
                report("Expected <%s> to be within <%s> of <%s>".formatted(actual, tolerance, expected));
            }
            return self();
        }

        public DoubleSubject isGreaterThan(double other) {
            if (!(actual > other)) {
                report("Expected <%s> to be greater than <%s>".formatted(actual, other));
            }
            return self();
        }

        public DoubleSubject isLessThan(double other) {
            if (!(actual < other)) {
                report("Expected <%s> to be less than <%s>".formatted(actual, other));
            }
            return self();
        }

        public double actual() {
            return actual;
        }
    }

    public static final class BooleanSubject extends AbstractSubject<BooleanSubject> {
        private final boolean actual;

        BooleanSubject(boolean actual) {
            this.actual = actual;
        }

        public BooleanSubject isTrue() {
            if (!actual) {
                report("Expected true but was false");
            }
            return self();
        }

        public BooleanSubject isFalse() {
            if (actual) {
                report("Expected false but was true");
            }
            return self();
        }
    }

    public static final class StringSubject extends AbstractSubject<StringSubject> {
        private final String actual;

        StringSubject(String actual) {
            this.actual = actual;
        }

        public StringSubject isEqualTo(String expected) {
            if (!Objects.equals(actual, expected)) {
                report("Expected <%s> but was <%s>".formatted(expected, actual));
            }
            return self();
        }

        public StringSubject contains(CharSequence expected) {
            if (actual == null || !actual.contains(expected)) {
                report("Expected <%s> to contain <%s>".formatted(actual, expected));
            }
            return self();
        }

        public StringSubject isEmpty() {
            if (actual == null || !actual.isEmpty()) {
                report("Expected an empty string but was <%s>".formatted(actual));
            }
            return self();
        }

        public StringSubject hasLength(int expected) {
            if (actual == null || actual.length() != expected) {
                report("Expected length <%d> but was <%s>"
                        .formatted(expected, actual == null ? "null" : actual.length()));
            }
            return self();
        }

        public StringSubject startsWith(String prefix) {
            if (actual == null || !actual.startsWith(prefix)) {
                report("Expected <%s> to start with <%s>".formatted(actual, prefix));
            }
            return self();
        }

        public StringSubject endsWith(String suffix) {
            if (actual == null || !actual.endsWith(suffix)) {
                report("Expected <%s> to end with <%s>".formatted(actual, suffix));
            }
            return self();
        }

        public StringSubject matches(String regex) {
            if (actual == null || !actual.matches(regex)) {
                report("Expected <%s> to match regex <%s>".formatted(actual, regex));
            }
            return self();
        }

        public StringSubject isEqualToIgnoringCase(String expected) {
            if (actual == null || !actual.equalsIgnoreCase(expected)) {
                report("Expected <%s> to equal (ignoring case) <%s>".formatted(actual, expected));
            }
            return self();
        }

        public StringSubject containsIgnoringCase(String expected) {
            if (actual == null
                    || !actual.toLowerCase(java.util.Locale.ROOT)
                            .contains(expected.toLowerCase(java.util.Locale.ROOT))) {
                report("Expected <%s> to contain (ignoring case) <%s>".formatted(actual, expected));
            }
            return self();
        }

        public StringSubject isBlank() {
            if (actual == null || !actual.isBlank()) {
                report("Expected a blank string but was <%s>".formatted(actual));
            }
            return self();
        }

        public StringSubject isNotBlank() {
            if (actual == null || actual.isBlank()) {
                report("Expected a non-blank string but was <%s>".formatted(actual));
            }
            return self();
        }

        public String actual() {
            return actual;
        }
    }

    public static final class OptionalSubject<T> extends AbstractSubject<OptionalSubject<T>> {
        private final Optional<T> actual;

        OptionalSubject(Optional<T> actual) {
            this.actual = actual;
        }

        public OptionalSubject<T> isPresent() {
            if (actual == null || actual.isEmpty()) {
                report("Expected a present Optional but was empty");
            }
            return self();
        }

        public OptionalSubject<T> isEmpty() {
            if (actual != null && actual.isPresent()) {
                report("Expected an empty Optional but was <%s>".formatted(actual.get()));
            }
            return self();
        }

        public OptionalSubject<T> hasValue(T expected) {
            if (actual == null || actual.isEmpty()) {
                report("Expected Optional value <%s> but was empty".formatted(expected));
                return self();
            }
            if (!Objects.equals(actual.get(), expected)) {
                report("Expected Optional value <%s> but was <%s>".formatted(expected, actual.get()));
            }
            return self();
        }

        /** Drills into the contained value for further assertions (over {@code null} if empty). */
        public Subject<T> which() {
            return new Subject<>(actual == null || actual.isEmpty() ? null : actual.get());
        }

        public Optional<T> actual() {
            return actual;
        }
    }

    public static final class IterableSubject<T> extends AbstractSubject<IterableSubject<T>> {
        private final Iterable<T> actual;
        private final List<T> items;

        IterableSubject(Iterable<T> actual) {
            this.actual = actual;
            this.items = actual == null ? null : toList(actual);
        }

        public IterableSubject<T> isEqualTo(Object expected) {
            if (!Objects.equals(actual, expected)) {
                report("Expected <%s> but was <%s>".formatted(expected, actual));
            }
            return self();
        }

        public IterableSubject<T> isEmpty() {
            if (items == null) {
                report("Expected an empty iterable but was null");
            } else if (!items.isEmpty()) {
                report("Expected an empty iterable but had %d element(s): %s".formatted(items.size(), items));
            }
            return self();
        }

        public IterableSubject<T> isNotEmpty() {
            if (items == null || items.isEmpty()) {
                report("Expected a non-empty iterable");
            }
            return self();
        }

        public IterableSubject<T> hasSize(int expected) {
            if (items == null) {
                report("Expected size <%d> but was null".formatted(expected));
            } else if (items.size() != expected) {
                report("Expected size <%d> but was <%d>: %s".formatted(expected, items.size(), items));
            }
            return self();
        }

        @SafeVarargs
        public final IterableSubject<T> contains(T... expected) {
            if (guardNull()) {
                return self();
            }
            for (T element : expected) {
                if (!items.contains(element)) {
                    report("Expected to contain <%s> but was %s".formatted(element, items));
                }
            }
            return self();
        }

        @SafeVarargs
        public final IterableSubject<T> doesNotContain(T... unexpected) {
            if (guardNull()) {
                return self();
            }
            for (T element : unexpected) {
                if (items.contains(element)) {
                    report("Expected not to contain <%s> but was %s".formatted(element, items));
                }
            }
            return self();
        }

        @SafeVarargs
        public final IterableSubject<T> containsExactly(T... expected) {
            if (guardNull()) {
                return self();
            }
            var want = Arrays.asList(expected);
            if (!items.equals(want)) {
                report("Expected exactly %s but was %s".formatted(want, items));
            }
            return self();
        }

        @SafeVarargs
        public final IterableSubject<T> containsExactlyInAnyOrder(T... expected) {
            if (guardNull()) {
                return self();
            }
            var remaining = new ArrayList<>(items);
            for (T element : expected) {
                if (!remaining.remove(element)) {
                    report("Expected to contain <%s> (in any order) but was %s".formatted(element, items));
                    return self();
                }
            }
            if (!remaining.isEmpty()) {
                report("Expected exactly %s in any order but had extra %s"
                        .formatted(Arrays.asList(expected), remaining));
            }
            return self();
        }

        public IterableSubject<T> allMatch(Predicate<? super T> predicate) {
            if (guardNull()) {
                return self();
            }
            for (T element : items) {
                if (!predicate.test(element)) {
                    report("Expected all elements to match, but <%s> did not".formatted(element));
                    return self();
                }
            }
            return self();
        }

        public IterableSubject<T> anyMatch(Predicate<? super T> predicate) {
            if (guardNull()) {
                return self();
            }
            for (T element : items) {
                if (predicate.test(element)) {
                    return self();
                }
            }
            report("Expected at least one element to match, but none did in %s".formatted(items));
            return self();
        }

        /** Applies {@code inspector} (which performs its own assertions) to every element. */
        public IterableSubject<T> allSatisfy(Consumer<? super T> inspector) {
            if (guardNull()) {
                return self();
            }
            for (T element : items) {
                inspector.accept(element);
            }
            return self();
        }

        /** Applies one inspector per element, in order; the element count must match the inspector count. */
        @SafeVarargs
        public final IterableSubject<T> satisfiesExactly(Consumer<? super T>... inspectors) {
            if (guardNull()) {
                return self();
            }
            if (items.size() != inspectors.length) {
                report("Expected exactly %d element(s) for satisfiesExactly but was %d: %s"
                        .formatted(inspectors.length, items.size(), items));
                return self();
            }
            for (int i = 0; i < inspectors.length; i++) {
                inspectors[i].accept(items.get(i));
            }
            return self();
        }

        private boolean guardNull() {
            if (items == null) {
                report("Expected a non-null iterable");
                return true;
            }
            return false;
        }

        public Iterable<T> actual() {
            return actual;
        }

        private static <E> List<E> toList(Iterable<E> iterable) {
            if (iterable instanceof List<E> list) {
                return list;
            }
            var list = new ArrayList<E>();
            for (E element : iterable) {
                list.add(element);
            }
            return list;
        }
    }

    public static final class MapSubject<K, V> extends AbstractSubject<MapSubject<K, V>> {
        private final Map<K, V> actual;

        MapSubject(Map<K, V> actual) {
            this.actual = actual;
        }

        public MapSubject<K, V> isEqualTo(Object expected) {
            if (!Objects.equals(actual, expected)) {
                report("Expected <%s> but was <%s>".formatted(expected, actual));
            }
            return self();
        }

        public MapSubject<K, V> isEmpty() {
            if (actual == null) {
                report("Expected an empty map but was null");
            } else if (!actual.isEmpty()) {
                report("Expected an empty map but had %d entr(ies): %s".formatted(actual.size(), actual));
            }
            return self();
        }

        public MapSubject<K, V> isNotEmpty() {
            if (actual == null || actual.isEmpty()) {
                report("Expected a non-empty map");
            }
            return self();
        }

        public MapSubject<K, V> hasSize(int expected) {
            if (actual == null) {
                report("Expected size <%d> but was null".formatted(expected));
            } else if (actual.size() != expected) {
                report("Expected size <%d> but was <%d>: %s".formatted(expected, actual.size(), actual));
            }
            return self();
        }

        public MapSubject<K, V> containsKey(K key) {
            if (actual == null || !actual.containsKey(key)) {
                report("Expected to contain key <%s> but was %s".formatted(key, actual));
            }
            return self();
        }

        public MapSubject<K, V> doesNotContainKey(K key) {
            if (actual != null && actual.containsKey(key)) {
                report("Expected not to contain key <%s> but was %s".formatted(key, actual));
            }
            return self();
        }

        public MapSubject<K, V> containsValue(V value) {
            if (actual == null || !actual.containsValue(value)) {
                report("Expected to contain value <%s> but was %s".formatted(value, actual));
            }
            return self();
        }

        public MapSubject<K, V> containsEntry(K key, V value) {
            if (actual == null || !actual.containsKey(key)) {
                report("Expected to contain key <%s> but was %s".formatted(key, actual));
            } else if (!Objects.equals(actual.get(key), value)) {
                report("Expected entry <%s>=<%s> but value was <%s>".formatted(key, value, actual.get(key)));
            }
            return self();
        }

        public Map<K, V> actual() {
            return actual;
        }
    }

    public static final class ThrowableSubject extends AbstractSubject<ThrowableSubject> {
        private final Throwable actual;

        ThrowableSubject(Throwable actual) {
            this.actual = actual;
        }

        public <T extends Throwable> ThrowableSubject isInstanceOf(Class<T> expectedType) {
            if (!expectedType.isInstance(actual)) {
                report(
                        "Expected exception of type <%s> but was <%s>"
                                .formatted(
                                        expectedType.getName(),
                                        actual == null
                                                ? "none"
                                                : actual.getClass().getName()),
                        actual);
            }
            return self();
        }

        public ThrowableSubject hasMessageContaining(String expected) {
            var message = actual == null ? null : actual.getMessage();
            if (message == null || !message.contains(expected)) {
                report("Expected exception message to contain <%s> but was <%s>".formatted(expected, message), actual);
            }
            return self();
        }

        public ThrowableSubject hasMessage(String expected) {
            var message = actual == null ? null : actual.getMessage();
            if (!Objects.equals(message, expected)) {
                report("Expected exception message <%s> but was <%s>".formatted(expected, message), actual);
            }
            return self();
        }

        public ThrowableSubject hasCauseInstanceOf(Class<? extends Throwable> expectedType) {
            var cause = actual == null ? null : actual.getCause();
            if (!expectedType.isInstance(cause)) {
                report(
                        "Expected cause of type <%s> but was <%s>"
                                .formatted(
                                        expectedType.getName(),
                                        cause == null
                                                ? "none"
                                                : cause.getClass().getName()),
                        actual);
            }
            return self();
        }

        public ThrowableSubject hasNoCause() {
            if (actual != null && actual.getCause() != null) {
                report(
                        "Expected no cause but was <%s>"
                                .formatted(actual.getCause().getClass().getName()),
                        actual);
            }
            return self();
        }

        /** Drills into the cause for further assertions. */
        public ThrowableSubject cause() {
            return new ThrowableSubject(actual == null ? null : actual.getCause());
        }

        /** Drills into the message for further assertions. */
        public StringSubject message() {
            return new StringSubject(actual == null ? null : actual.getMessage());
        }

        public Throwable actual() {
            return actual;
        }
    }
}
