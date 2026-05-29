package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.ForAll;
import su.kidoz.axiomj.api.IntRange;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Property;
import su.kidoz.axiomj.api.Scenario;
import su.kidoz.axiomj.api.StringLength;

@ProductArea("Property testing")
@Feature(id = "property.records", name = "Record generators", owner = "quality-team")
public final class RecordPropertyTest {
    record UserInput(
            @StringLength(max = 12) String name,
            @IntRange(min = 0, max = 120) int age) {}

    @Property(tries = 100, seed = 777L)
    @Scenario("record generators respect component annotations")
    void generatedRecordsRespectComponentAnnotations(@ForAll UserInput input) {
        expect(input.name().length()).isBetween(0, 12);
        expect(input.age()).isBetween(0, 120);
    }
}
