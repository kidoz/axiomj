package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.ForAll;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Property;
import su.kidoz.axiomj.property.Arbitrary;
import su.kidoz.axiomj.property.GenerationContext;

@ProductArea("Core")
@Feature(id = "property.custom-generator", name = "Custom @ForAll generators", owner = "core-team")
public final class CustomGeneratorTest {

    record Point(int x, int y) {}

    /**
     * A custom generator that only ever produces points in the first quadrant. If the engine ignored it and fell back
     * to the built-in record generator (which produces negative coordinates too), this property would fail — so a
     * passing run proves the custom generator is actually used.
     */
    public static final class FirstQuadrantPoints implements Arbitrary<Point> {
        @Override
        public Point generate(GenerationContext context) {
            return new Point(
                    context.random().nextInt(0, 1_000), context.random().nextInt(0, 1_000));
        }
    }

    @Property(tries = 200)
    void customGeneratorOnlyProducesFirstQuadrant(@ForAll(gen = FirstQuadrantPoints.class) Point p) {
        expect(p.x() >= 0).isTrue();
        expect(p.y() >= 0).isTrue();
    }
}
