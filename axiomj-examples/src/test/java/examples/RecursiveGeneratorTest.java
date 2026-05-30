package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.util.List;
import java.util.stream.IntStream;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.ForAll;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Property;
import su.kidoz.axiomj.property.Arbitrary;
import su.kidoz.axiomj.property.GenerationContext;

@ProductArea("Core")
@Feature(id = "property.recursive", name = "Recursive/Sized Generators", owner = "core-team")
public final class RecursiveGeneratorTest {

    record Node(String name, List<Node> children) {}

    public static final class NodeGenerator implements Arbitrary<Node> {
        private final Arbitrary<Node> lazyGen = Arbitrary.lazy(() -> this);

        @Override
        public Node generate(GenerationContext context) {
            String name = "Node-" + context.random().nextInt(100);
            if (context.size() <= 0) {
                return new Node(name, List.of());
            }

            int numChildren = context.random().nextInt(0, 4);
            // Decrease size for children to ensure termination
            var childContext = context.withSize(context.size() / 2 - 1);
            List<Node> children = IntStream.range(0, numChildren)
                    .mapToObj(_ -> lazyGen.generate(childContext))
                    .toList();
            return new Node(name, children);
        }
    }

    @Property(tries = 50)
    void canGenerateRecursiveStructures(@ForAll(gen = NodeGenerator.class) Node root) {
        expect(root).isNotNull();
        expect(depth(root)).isLessThanOrEqualTo(10); // Since default size is at most 100, and we half it.
    }

    private int depth(Node node) {
        if (node.children().isEmpty()) {
            return 1;
        }
        return 1 + node.children().stream().mapToInt(this::depth).max().orElse(0);
    }
}
