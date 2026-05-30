package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.util.List;
import java.util.Map;
import java.util.Set;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.ForAll;
import su.kidoz.axiomj.api.Property;

@Feature(id = "property.collections", name = "Collection generators")
class CollectionPropertyTest {

    @Property
    void generatesLists(@ForAll List<Integer> numbers) {
        expect(numbers.size() <= 10).isTrue();
    }

    @Property
    void generatesSets(@ForAll Set<String> strings) {
        expect(strings.size() <= 10).isTrue();
    }

    @Property
    void generatesMaps(@ForAll Map<String, Boolean> flags) {
        expect(flags.size() <= 10).isTrue();
    }
}
