package su.kidoz.axiomj.mock;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import su.kidoz.axiomj.assertions.AssertionFailed;

public final class Mocks {
    private Mocks() {}

    private static final ScopedValue<CaptureMode> CAPTURE_MODE = ScopedValue.newInstance();
    private static final ScopedValue<List<MockController>> SESSION = ScopedValue.newInstance();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final Map<Object, MockController> NON_PROXY_MOCKS = Collections.synchronizedMap(new WeakHashMap<>());

    /** Creates a lenient mock: unused stubs are tolerated. */
    public static <T> T mock(Class<T> type) {
        return mock(type, false);
    }

    /**
     * Creates a mock. When {@code strict} is true, stubs configured on it must be matched at least once during the
     * test, otherwise {@link #verifyStrictStubs()} fails (a stub configured but never matched is reported).
     */
    public static <T> T mock(Class<T> type, boolean strict) {
        return newProxy(type, new MockController(type, strict, null, false));
    }

    /**
     * Creates a partial mock backed by a real interface implementation: unstubbed calls delegate to {@code instance},
     * stubbed calls use the stub.
     */
    public static <T> T spy(Class<T> type, T instance) {
        Objects.requireNonNull(instance, "instance");
        return newProxy(type, new MockController(type, false, instance, false));
    }

    /**
     * Creates a deep mock: unstubbed methods returning an interface yield further deep mocks, so chained calls such as
     * {@code when(() -> a.getB().getName()).thenReturn(...)} work without mocking each link. Limited to no-argument
     * intermediate calls.
     */
    public static <T> T mockDeep(Class<T> type) {
        return newProxy(type, new MockController(type, false, null, true));
    }

    /**
     * SPI for the optional {@code axiomj-mock-bytecode} module: returns a fresh engine handler for {@code type},
     * registered in the current strict session. The bytecode module routes a generated subclass's calls to it so that
     * class mocks reuse the whole stubbing/verification engine.
     */
    public static InvocationHandler newControllerHandler(Class<?> type, boolean strict) {
        var controller = new MockController(type, strict, null, false);
        register(controller);
        return controller;
    }

    /**
     * SPI for the bytecode module: binds a non-proxy mock instance to its engine handler so that
     * {@link #verifyNoMoreInteractions} / {@link #verifyNoInteractions} accept class mocks too.
     */
    public static void bindInstance(Object mock, InvocationHandler handler) {
        if (handler instanceof MockController controller) {
            NON_PROXY_MOCKS.put(mock, controller);
        }
    }

    private static <T> T newProxy(Class<T> type, MockController controller) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException(
                    "The dependency-free mock engine supports interfaces only: " + type.getName());
        }
        register(controller);
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, controller);
        return type.cast(proxy);
    }

    /**
     * Runs {@code body} inside a strict-stub tracking scope: mocks created during it are recorded so that
     * {@link #verifyStrictStubs()} can report unmatched strict stubs. Used by the engine to wrap each test invocation.
     */
    public static <T> T inSession(Supplier<T> body) {
        return ScopedValue.where(SESSION, new ArrayList<MockController>()).call(body::get);
    }

    /** Fails if any strict mock created in the current session has a stub that was never matched. */
    public static void verifyStrictStubs() {
        if (!SESSION.isBound()) {
            return;
        }
        for (var controller : SESSION.get()) {
            if (controller.strict) {
                var unused = controller.firstUnusedStub();
                if (unused != null) {
                    throw new AssertionFailed("Unnecessary stubbing on mock(%s): %s was never matched"
                            .formatted(controller.type.getName(), unused.signature()));
                }
            }
        }
    }

    private static void register(MockController controller) {
        if (SESSION.isBound()) {
            SESSION.get().add(controller);
        }
    }

    public static <T> OngoingStubbing<T> when(Supplier<T> call) {
        return new OngoingStubbing<>(captureStub(call::get));
    }

    public static OngoingStubbing<Void> whenVoid(ThrowingRunnable call) {
        return new OngoingStubbing<>(captureStub(call));
    }

    public static Verification verify(Runnable call) {
        var capture = capture(CaptureKind.VERIFY, () -> {
            call.run();
            return null;
        });
        return new Verification(capture.controller(), capture.matched());
    }

    public static <T> Verification verify(Supplier<T> call) {
        var capture = capture(CaptureKind.VERIFY, call::get);
        return new Verification(capture.controller(), capture.matched());
    }

    /** Begins order-sensitive verification across the given mocks. */
    public static InOrder inOrder(Object... mocks) {
        return new InOrder();
    }

    /** BDD alias for {@link #when}: {@code given(() -> mock.call()).willReturn(...)}. */
    public static <T> BddStubbing<T> given(Supplier<T> call) {
        return new BddStubbing<>(when(call));
    }

    /** BDD alias for {@link #whenVoid}. */
    public static BddStubbing<Void> givenVoid(ThrowingRunnable call) {
        return new BddStubbing<>(whenVoid(call));
    }

    /** Fails if any recorded interaction on a mock was never verified. */
    public static void verifyNoMoreInteractions(Object... mocks) {
        for (var mock : mocks) {
            var controller = controllerOf(mock);
            var leftover = controller.firstUnverified();
            if (leftover != null) {
                throw new AssertionFailed("No more interactions expected on mock(%s), but found: %s"
                        .formatted(controller.type.getName(), leftover.signature()));
            }
        }
    }

    /** Fails if any interaction at all was recorded on a mock. */
    public static void verifyNoInteractions(Object... mocks) {
        for (var mock : mocks) {
            var controller = controllerOf(mock);
            int count = controller.recordedCount();
            if (count > 0) {
                throw new AssertionFailed("Expected no interactions on mock(%s), but found %d"
                        .formatted(controller.type.getName(), count));
            }
        }
    }

    public interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private static StubRule captureStub(ThrowingRunnable call) {
        var capture = capture(CaptureKind.STUB, () -> {
            try {
                call.run();
            } catch (Throwable e) {
                e.printStackTrace();
                throw new RuntimeException("Stub capture failed", e);
            }
            return null;
        });
        return capture.controller().newRule(capture.matched());
    }

    private static Capture capture(CaptureKind kind, Supplier<?> call) {
        var mode = new CaptureMode(kind);
        ScopedValue.where(CAPTURE_MODE, mode)
                .where(Arg.STACK, new ArrayDeque<ArgMatcher>())
                .run(call::get);
        return mode.requiredCapture();
    }

    private static MockController controllerOf(Object mock) {
        if (Proxy.isProxyClass(mock.getClass())
                && Proxy.getInvocationHandler(mock) instanceof MockController controller) {
            return controller;
        }
        var bound = NON_PROXY_MOCKS.get(mock);
        if (bound != null) {
            return bound;
        }
        throw new IllegalArgumentException("Not an AxiomJ mock: " + mock);
    }

    public static final class OngoingStubbing<T> {
        private final StubRule rule;

        private OngoingStubbing(StubRule rule) {
            this.rule = rule;
        }

        @SafeVarargs
        public final OngoingStubbing<T> thenReturn(T value, T... more) {
            rule.answers.add(_ -> value);
            for (T extra : more) {
                rule.answers.add(_ -> extra);
            }
            return this;
        }

        public OngoingStubbing<T> thenAnswer(Answer<T> answer) {
            rule.answers.add(answer);
            return this;
        }

        public OngoingStubbing<T> thenThrow(Throwable error) {
            Objects.requireNonNull(error, "error");
            rule.answers.add(_ -> {
                throw error;
            });
            return this;
        }
    }

    public static final class Verification {
        private final MockController controller;
        private final MatchedInvocation matched;

        private Verification(MockController controller, MatchedInvocation matched) {
            this.controller = controller;
            this.matched = matched;
        }

        public void calledOnce() {
            calledTimes(1);
        }

        public void never() {
            calledTimes(0);
        }

        public void atLeastOnce() {
            atLeast(1);
        }

        public void calledTimes(int expected) {
            int actual = controller.verifyCount(matched);
            if (actual != expected) {
                fail("exactly " + expected, actual);
            }
        }

        public void atLeast(int expected) {
            int actual = controller.verifyCount(matched);
            if (actual < expected) {
                fail("at least " + expected, actual);
            }
        }

        public void atMost(int expected) {
            int actual = controller.verifyCount(matched);
            if (actual > expected) {
                fail("at most " + expected, actual);
            }
        }

        /** Waits up to {@code timeout} for the call-count condition to hold (for asynchronous interactions). */
        public TimedVerification within(Duration timeout) {
            return new TimedVerification(controller, matched, timeout);
        }

        private void fail(String expected, int actual) {
            throw new AssertionFailed("Expected %s to be called %s time(s), but was called %d time(s)"
                    .formatted(matched.signature(), expected, actual));
        }
    }

    public static final class TimedVerification {
        private final MockController controller;
        private final MatchedInvocation matched;
        private final Duration timeout;

        private TimedVerification(MockController controller, MatchedInvocation matched, Duration timeout) {
            this.controller = controller;
            this.matched = matched;
            this.timeout = timeout;
        }

        public void calledOnce() {
            calledTimes(1);
        }

        public void atLeastOnce() {
            atLeast(1);
        }

        public void calledTimes(int expected) {
            await(actual -> actual == expected, "exactly " + expected);
        }

        public void atLeast(int expected) {
            await(actual -> actual >= expected, "at least " + expected);
        }

        private void await(IntPredicate condition, String description) {
            long deadline = System.nanoTime() + timeout.toNanos();
            int actual = controller.verifyCount(matched);
            while (!condition.test(actual) && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                actual = controller.verifyCount(matched);
            }
            if (!condition.test(actual)) {
                throw new AssertionFailed("Expected %s to be called %s time(s) within %d ms, but was called %d time(s)"
                        .formatted(matched.signature(), description, timeout.toMillis(), actual));
            }
        }
    }

    public static final class InOrder {
        private long lastSequence = -1;

        public InOrder verify(Runnable call) {
            return verifyCapture(capture(CaptureKind.VERIFY, () -> {
                call.run();
                return null;
            }));
        }

        public <T> InOrder verify(Supplier<T> call) {
            return verifyCapture(capture(CaptureKind.VERIFY, call::get));
        }

        private InOrder verifyCapture(Capture capture) {
            long found = capture.controller().verifyInOrder(capture.matched(), lastSequence);
            if (found < 0) {
                throw new AssertionFailed(
                        "Expected %s in order, but it was not invoked after the previous verified call"
                                .formatted(capture.matched().signature()));
            }
            lastSequence = found;
            return this;
        }
    }

    public static final class BddStubbing<T> {
        private final OngoingStubbing<T> stubbing;

        private BddStubbing(OngoingStubbing<T> stubbing) {
            this.stubbing = stubbing;
        }

        @SafeVarargs
        public final BddStubbing<T> willReturn(T value, T... more) {
            stubbing.thenReturn(value, more);
            return this;
        }

        public BddStubbing<T> willAnswer(Answer<T> answer) {
            stubbing.thenAnswer(answer);
            return this;
        }

        public BddStubbing<T> willThrow(Throwable error) {
            stubbing.thenThrow(error);
            return this;
        }
    }

    private enum CaptureKind {
        STUB,
        VERIFY
    }

    private static final class CaptureMode {
        private final CaptureKind kind;
        private Capture capture;

        private CaptureMode(CaptureKind kind) {
            this.kind = kind;
        }

        private void capture(MockController controller, MatchedInvocation matched) {
            this.capture = new Capture(controller, matched);
        }

        private Capture requiredCapture() {
            if (capture == null) {
                throw new IllegalStateException("No mock invocation was captured for " + kind);
            }
            return capture;
        }
    }

    private record Capture(MockController controller, MatchedInvocation matched) {}

    private record MatchedInvocation(Class<?> mockType, Method method, List<ArgMatcher> matchers) {
        boolean matches(Invocation actual) {
            if (!method.equals(actual.method())) {
                return false;
            }
            var args = actual.arguments();
            if (matchers.size() != args.size()) {
                return false;
            }
            for (int i = 0; i < args.size(); i++) {
                if (!matchers.get(i).matches(args.get(i))) {
                    return false;
                }
            }
            return true;
        }

        String signature() {
            return mockType.getSimpleName() + "." + method.getName() + "(" + matchers.size() + " arg matcher(s))";
        }
    }

    private static final class StubRule {
        private final MatchedInvocation matched;
        private final ArrayDeque<Answer<?>> answers = new ArrayDeque<>();
        private boolean used;

        private StubRule(MatchedInvocation matched) {
            this.matched = matched;
        }
    }

    private static final class MockController implements InvocationHandler {
        private final Class<?> type;
        private final boolean strict;
        private final Object delegate;
        private final boolean deep;
        private final List<StubRule> rules = new ArrayList<>();
        private final List<Invocation> invocations = new ArrayList<>();
        private final List<Long> sequences = new ArrayList<>();
        private final Set<Integer> verified = new HashSet<>();
        private final Map<Invocation, Object> deepChildren = new HashMap<>();

        private MockController(Class<?> type, boolean strict, Object delegate, boolean deep) {
            this.type = type;
            this.strict = strict;
            this.delegate = delegate;
            this.deep = deep;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "mock(" + type.getName() + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.invoke(this, args);
                };
            }

            if (CAPTURE_MODE.isBound()) {
                var mode = CAPTURE_MODE.get();
                var captured = Invocation.from(type, method, args);
                mode.capture(this, new MatchedInvocation(type, method, resolveMatchers(method, args)));
                if (deep && method.getReturnType().isInterface()) {
                    return deepChild(captured, method.getReturnType());
                }
                return defaultValue(method.getReturnType());
            }

            var invocation = Invocation.from(type, method, args);
            Answer<?> answer = null;
            synchronized (this) {
                invocations.add(invocation);
                sequences.add(SEQUENCE.getAndIncrement());
                var rule = findRule(invocation);
                if (rule != null) {
                    rule.used = true;
                    if (!rule.answers.isEmpty()) {
                        answer = rule.answers.size() == 1 ? rule.answers.peekFirst() : rule.answers.removeFirst();
                    }
                }
            }
            if (answer != null) {
                return answer.answer(invocation);
            }
            if (delegate != null) {
                try {
                    method.setAccessible(true);
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
            if (deep && method.getReturnType().isInterface()) {
                return deepChild(invocation, method.getReturnType());
            }
            return defaultValue(method.getReturnType());
        }

        private synchronized Object deepChild(Invocation invocation, Class<?> childType) {
            return deepChildren.computeIfAbsent(invocation, _ -> Mocks.mockDeep(childType));
        }

        private List<ArgMatcher> resolveMatchers(Method method, Object[] args) {
            int count = args == null ? 0 : args.length;
            var pushed = Arg.drain();
            if (pushed.isEmpty()) {
                var exact = new ArrayList<ArgMatcher>(count);
                for (int i = 0; i < count; i++) {
                    Object expected = args[i];
                    exact.add(actual -> Objects.equals(actual, expected));
                }
                return exact;
            }
            if (pushed.size() != count) {
                throw new IllegalStateException(
                        "Mixed raw arguments and Arg matchers in call to %s.%s: expected %d matcher(s) but got %d. Wrap literals with Arg.eq(...)."
                                .formatted(type.getSimpleName(), method.getName(), count, pushed.size()));
            }
            return pushed;
        }

        private synchronized StubRule newRule(MatchedInvocation matched) {
            var rule = new StubRule(matched);
            rules.add(rule);
            return rule;
        }

        private StubRule findRule(Invocation invocation) {
            for (int i = rules.size() - 1; i >= 0; i--) {
                if (rules.get(i).matched.matches(invocation)) {
                    return rules.get(i);
                }
            }
            return null;
        }

        private synchronized int verifyCount(MatchedInvocation matched) {
            int count = 0;
            for (int i = 0; i < invocations.size(); i++) {
                if (matched.matches(invocations.get(i))) {
                    count++;
                    verified.add(i);
                }
            }
            return count;
        }

        private synchronized Invocation firstUnverified() {
            for (int i = 0; i < invocations.size(); i++) {
                if (!verified.contains(i)) {
                    return invocations.get(i);
                }
            }
            return null;
        }

        private synchronized long verifyInOrder(MatchedInvocation matched, long afterSequence) {
            for (int i = 0; i < invocations.size(); i++) {
                if (sequences.get(i) > afterSequence && matched.matches(invocations.get(i))) {
                    verified.add(i);
                    return sequences.get(i);
                }
            }
            return -1;
        }

        private synchronized MatchedInvocation firstUnusedStub() {
            for (var rule : rules) {
                if (!rule.used) {
                    return rule.matched;
                }
            }
            return null;
        }

        private synchronized int recordedCount() {
            return invocations.size();
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == void.class) return null;
        if (!returnType.isPrimitive()) {
            if (returnType == Optional.class) return Optional.empty();
            if (returnType == List.class) return List.of();
            if (returnType == Set.class) return Set.of();
            if (returnType == Map.class) return Map.of();
            if (returnType == Stream.class) return Stream.empty();
            if (returnType == CompletableFuture.class) return CompletableFuture.completedFuture(null);
            return null;
        }
        if (returnType == boolean.class) return false;
        if (returnType == char.class) return '\0';
        if (returnType == byte.class) return (byte) 0;
        if (returnType == short.class) return (short) 0;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == float.class) return 0f;
        if (returnType == double.class) return 0d;
        throw new IllegalArgumentException("Unsupported primitive return type: " + returnType);
    }
}
