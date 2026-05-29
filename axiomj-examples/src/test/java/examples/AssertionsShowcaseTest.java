package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;
import static su.kidoz.axiomj.assertions.Expect.expectThrown;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Scenario;
import su.kidoz.axiomj.assertions.Expect;

@ProductArea("Testing")
@Feature(id = "assert.fluent", name = "Fluent assertions", owner = "core-team")
public final class AssertionsShowcaseTest {

    @Fact(name = "collection assertions")
    @Scenario("iterable subject supports size, membership, order, and per-element inspection")
    void collections() {
        var users = List.of("ada", "bob", "cy");

        expect(users).hasSize(3).contains("ada", "cy").doesNotContain("zoe");
        expect(users).containsExactly("ada", "bob", "cy");
        expect(users).containsExactlyInAnyOrder("cy", "ada", "bob");
        expect(users).allMatch(u -> !u.isEmpty());
        expect(users).allSatisfy(u -> expect(u.length()).isBetween(1, 10));
        expect(users)
                .satisfiesExactly(
                        u -> expect(u).isEqualTo("ada"),
                        u -> expect(u).isEqualTo("bob"),
                        u -> expect(u).isEqualTo("cy"));
    }

    @Fact(name = "map assertions")
    @Scenario("map subject supports keys, values, and entries")
    void maps() {
        var config = Map.of("env", "prod", "region", "eu");

        expect(config)
                .hasSize(2)
                .containsKey("env")
                .containsEntry("region", "eu")
                .containsValue("prod")
                .doesNotContainKey("debug");
    }

    @Fact(name = "soft assertions aggregate")
    @Scenario("softly runs every assertion within the scope")
    void softAssertions() {
        var order = List.of("widget", "gadget");

        Expect.softly(() -> {
            expect(order).hasSize(2);
            expect(order).contains("widget");
            expect(order.size()).isEqualTo(2);
        });
    }

    @Fact(name = "because reason")
    @Scenario("as(...) attaches a description used in failure messages")
    void becauseReason() {
        expect("Hello").as("greeting prefix").contains("Hell");
    }

    @Fact(name = "deep structural equivalence")
    @Scenario("isEquivalentTo compares object graphs field-by-field, ignoring identity")
    void deepEquivalence() {
        var a = new Order("A-1", List.of(new Line("widget", 2), new Line("gadget", 1)));
        var b = new Order("A-1", List.of(new Line("widget", 2), new Line("gadget", 1)));

        expect(a).isEquivalentTo(b);
    }

    @Fact(name = "equivalence ignoring fields")
    @Scenario("ignoringFields excludes volatile fields from the comparison")
    void equivalenceIgnoringFields() {
        var a = new Order("A-1", List.of(new Line("widget", 2)));
        var b = new Order("A-2", List.of(new Line("widget", 2)));

        expect(a).ignoringFields("id").isEquivalentTo(b);
    }

    @Fact(name = "numeric comparisons")
    @Scenario("int/long/double subjects support ordering and approximate equality")
    void numericComparisons() {
        expect(7).isGreaterThan(3).isLessThanOrEqualTo(7).isBetween(0, 10);
        expect(100L).isGreaterThanOrEqualTo(100L).isLessThan(200L);
        expect(3.14159).isCloseTo(3.14, 0.01).isGreaterThan(3.0);
    }

    @Fact(name = "string matchers")
    @Scenario("string subject supports prefix/suffix/regex/case-insensitive checks")
    void stringMatchers() {
        expect("AxiomJ").startsWith("Ax").endsWith("J").matches("[A-Za-z]+").isEqualToIgnoringCase("axiomj");
        expect("   ").isBlank();
        expect("x").isNotBlank().containsIgnoringCase("X");
    }

    @Fact(name = "exception details")
    @Scenario("throwable subject inspects type, exact message, and cause")
    void exceptionDetails() {
        expectThrown(() -> {
                    throw new IllegalStateException("boom", new IllegalArgumentException("root"));
                })
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom")
                .hasCauseInstanceOf(IllegalArgumentException.class);
        expectThrown(() -> {
                    throw new RuntimeException("standalone");
                })
                .hasNoCause();
    }

    @Fact(name = "does not throw and completes within budget")
    @Scenario("doesNotThrow and completesWithin guard behavior and timing")
    void doesNotThrowAndTiming() {
        Expect.doesNotThrow(() -> Integer.parseInt("42"));
        Expect.completesWithin(Duration.ofSeconds(2), () -> Thread.sleep(5));
    }

    @Fact(name = "comparable ordering")
    @Scenario("Comparable values support ordering assertions on the generic subject")
    void comparableOrdering() {
        expect(BigDecimal.valueOf(10)).isGreaterThan(BigDecimal.valueOf(3)).isLessThanOrEqualTo(BigDecimal.valueOf(10));
    }

    @Fact(name = "which drilling")
    @Scenario("which() drills into an Optional value for further assertions")
    void whichDrilling() {
        expect(Optional.of("Ada")).isPresent().which().isEqualTo("Ada");
    }

    record Order(String id, List<Line> lines) {}

    record Line(String sku, int quantity) {}
}
