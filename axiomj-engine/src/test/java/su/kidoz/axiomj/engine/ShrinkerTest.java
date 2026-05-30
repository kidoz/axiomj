package su.kidoz.axiomj.engine;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.lang.annotation.Annotation;
import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.property.Shrinker;

/** Dogfood: AxiomJ tests its own property shrinker — the candidates it offers toward simpler values. */
class ShrinkerTest {

    private static final Annotation[] NONE = new Annotation[0];

    @Fact(name = "ints shrink toward zero")
    void intsShrinkTowardZero() {
        var candidates = Shrinker.candidates(int.class, int.class, NONE, 128);
        expect(candidates.contains(0)).isTrue();
        expect(candidates.contains(64)).isTrue();
    }

    @Fact(name = "zero ints offer no step-away candidate")
    void zeroIntDoesNotStepAway() {
        // The old shrinker emitted -1 for value 0, moving *away* from the simplest value.
        var candidates = Shrinker.candidates(int.class, int.class, NONE, 0);
        expect(candidates.contains(-1)).isFalse();
        expect(candidates.contains(1)).isFalse();
    }

    @Fact(name = "doubles shrink toward zero")
    void doublesShrinkTowardZero() {
        var candidates = Shrinker.candidates(double.class, double.class, NONE, 8.0);
        expect(candidates.contains(0.0)).isTrue();
        expect(candidates.contains(4.0)).isTrue();
    }
}
