package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;
import static su.kidoz.axiomj.assertions.Expect.expectThrown;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Scenario;
import su.kidoz.axiomj.assertions.AssertionFailed;
import su.kidoz.axiomj.assertions.Expect;

/**
 * Exercises the failure branches of every {@code Expect} subject. The showcase test covers passing assertions; this one
 * verifies each matcher actually fails (throwing {@link AssertionFailed}) when its condition is not met, which is where
 * most of the assertion code lives.
 */
@ProductArea("Assertions")
@Feature(id = "assert.failures", name = "Assertion failure paths", owner = "core-team")
public final class AssertionFailureCoverageTest {

    /** Asserts that {@code action} fails with {@link AssertionFailed}. */
    private static void fails(Expect.ThrowingRunnable action) {
        expectThrown(action).isInstanceOf(AssertionFailed.class);
    }

    @Fact(name = "int subject failures")
    @Scenario("every IntSubject matcher reports when its condition is not met")
    void intFailures() {
        fails(() -> expect(1).isEqualTo(2));
        fails(() -> expect(5).isBetween(10, 20));
        fails(() -> expect(1).isGreaterThan(1));
        fails(() -> expect(1).isGreaterThanOrEqualTo(2));
        fails(() -> expect(2).isLessThan(2));
        fails(() -> expect(3).isLessThanOrEqualTo(2));
    }

    @Fact(name = "long subject failures")
    @Scenario("every LongSubject matcher reports when its condition is not met")
    void longFailures() {
        fails(() -> expect(1L).isEqualTo(2L));
        fails(() -> expect(1L).isGreaterThan(1L));
        fails(() -> expect(1L).isGreaterThanOrEqualTo(2L));
        fails(() -> expect(2L).isLessThan(2L));
        fails(() -> expect(3L).isLessThanOrEqualTo(2L));
    }

    @Fact(name = "double subject failures")
    @Scenario("DoubleSubject reports on tolerance and ordering misses, including NaN")
    void doubleFailures() {
        fails(() -> expect(1.0).isCloseTo(2.0, 0.1));
        fails(() -> expect(Double.NaN).isCloseTo(0.0, 1.0));
        fails(() -> expect(1.0).isGreaterThan(1.0));
        fails(() -> expect(1.0).isLessThan(1.0));
    }

    @Fact(name = "boolean subject failures")
    @Scenario("BooleanSubject reports on the wrong truth value")
    void booleanFailures() {
        fails(() -> expect(false).isTrue());
        fails(() -> expect(true).isFalse());
    }

    @Fact(name = "string subject failures")
    @Scenario("every StringSubject matcher reports when its condition is not met")
    void stringFailures() {
        fails(() -> expect("a").isEqualTo("b"));
        fails(() -> expect("abc").contains("z"));
        fails(() -> expect("abc").isEmpty());
        fails(() -> expect("abc").hasLength(2));
        fails(() -> expect("abc").startsWith("x"));
        fails(() -> expect("abc").endsWith("x"));
        fails(() -> expect("abc").matches("\\d+"));
        fails(() -> expect("abc").isEqualToIgnoringCase("xyz"));
        fails(() -> expect("abc").containsIgnoringCase("ZZZ"));
        fails(() -> expect("abc").isBlank());
        fails(() -> expect("   ").isNotBlank());
    }

    @Fact(name = "generic subject failures")
    @Scenario("Subject equality, nullness, and Comparable ordering report on misses")
    void genericFailures() {
        // A non-String/primitive value resolves to the generic Subject<T> (String/int/... have their own subjects).
        Object actual = "x";
        fails(() -> expect(actual).isEqualTo("y"));
        fails(() -> expect(actual).isNotEqualTo("x"));
        fails(() -> expect(actual).isNull());
        fails(() -> expect((Object) null).isNotNull());
        // Boxed values resolve to the generic Subject<T>, exercising its Comparable ordering path.
        fails(() -> expect(Integer.valueOf(1)).isGreaterThan(2));
        fails(() -> expect(Integer.valueOf(2)).isLessThan(1));
        fails(() -> expect(Integer.valueOf(1)).isGreaterThanOrEqualTo(2));
        fails(() -> expect(Integer.valueOf(2)).isLessThanOrEqualTo(1));
        // A non-Comparable actual reports rather than throwing ClassCastException.
        fails(() -> expect(new Object()).isLessThan(new Object()));
    }

    @Fact(name = "optional subject failures")
    @Scenario("OptionalSubject reports on presence/absence/value mismatches")
    void optionalFailures() {
        fails(() -> expect(Optional.empty()).isPresent());
        fails(() -> expect(Optional.of("a")).isEmpty());
        fails(() -> expect(Optional.of("a")).hasValue("b"));
        fails(() -> expect(Optional.empty()).hasValue("b"));
    }

    @Fact(name = "iterable subject failures")
    @Scenario("every IterableSubject matcher reports when its condition is not met")
    void iterableFailures() {
        var list = List.of("a", "b");
        fails(() -> expect(list).isEmpty());
        fails(() -> expect(List.of()).isNotEmpty());
        fails(() -> expect(list).hasSize(3));
        fails(() -> expect(list).contains("z"));
        fails(() -> expect(list).doesNotContain("a"));
        fails(() -> expect(list).containsExactly("a"));
        fails(() -> expect(list).containsExactlyInAnyOrder("a", "z"));
        fails(() -> expect(list).allMatch(s -> s.equals("a")));
        fails(() -> expect(list).anyMatch(s -> s.equals("z")));
        fails(() -> expect(list).satisfiesExactly(s -> expect(s).isEqualTo("a")));
    }

    @Fact(name = "map subject failures")
    @Scenario("every MapSubject matcher reports when its condition is not met")
    void mapFailures() {
        var map = Map.of("k", 1);
        fails(() -> expect(map).isEmpty());
        fails(() -> expect(Map.of()).isNotEmpty());
        fails(() -> expect(map).hasSize(2));
        fails(() -> expect(map).containsKey("missing"));
        fails(() -> expect(map).doesNotContainKey("k"));
        fails(() -> expect(map).containsValue(99));
        fails(() -> expect(map).containsEntry("k", 99));
    }

    @Fact(name = "throwable subject failures")
    @Scenario("ThrowableSubject reports on type, message, and cause mismatches")
    void throwableFailures() {
        // A ThrowableSubject is obtained from expectThrown(...); each matcher is given a wrong expectation.
        Expect.ThrowingRunnable boom = () -> {
            throw new IllegalStateException("boom", new IllegalArgumentException("root"));
        };
        fails(() -> expectThrown(boom).isInstanceOf(IOException.class));
        fails(() -> expectThrown(boom).hasMessage("nope"));
        fails(() -> expectThrown(boom).hasMessageContaining("nope"));
        fails(() -> expectThrown(boom).hasCauseInstanceOf(NullPointerException.class));
        fails(() -> expectThrown(boom).hasNoCause());
    }

    @Fact(name = "expectThrown and fail")
    @Scenario("expectThrown reports when nothing is thrown; fail always reports")
    void expectThrownAndFail() {
        fails(() -> expectThrown(() -> {}).isInstanceOf(RuntimeException.class));
        fails(() -> Expect.fail("forced failure"));
    }
}
