package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.ForAll;
import su.kidoz.axiomj.api.IntRange;
import su.kidoz.axiomj.api.Owner;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Property;
import su.kidoz.axiomj.api.Requirement;
import su.kidoz.axiomj.api.Scenario;

@ProductArea("Core")
@Feature(id = "core.calculator", name = "Calculator arithmetic", owner = "core-team")
@Owner("core-team")
public final class CalculatorTest {
    private final Calculator calculator = new Calculator();

    @Fact(tags = "unit")
    @Scenario("two integer values can be added")
    @Requirement("REQ-CALC-001")
    void addsTwoNumbers() {
        expect(calculator.add(2, 2)).isEqualTo(4);
    }

    @Property(tries = 200, seed = 123456789L, tags = "property")
    @Scenario("addition is commutative for generated integer pairs")
    @Requirement("REQ-CALC-002")
    void additionIsCommutative(
            @ForAll @IntRange(min = -10_000, max = 10_000) int a,
            @ForAll @IntRange(min = -10_000, max = 10_000) int b) {
        expect(calculator.add(a, b)).isEqualTo(calculator.add(b, a));
    }

    @Property(tries = 100, seed = 42L)
    @Scenario("absolute value is never negative")
    void absoluteValueIsNeverNegative(@ForAll @IntRange(min = -100_000, max = 100_000) int value) {
        expect(calculator.abs(value) >= 0).isTrue();
    }
}
