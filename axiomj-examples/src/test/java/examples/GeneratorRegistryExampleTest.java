package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.util.UUID;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.ForAll;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Property;
import su.kidoz.axiomj.api.Scenario;
import su.kidoz.axiomj.api.UseModules;
import su.kidoz.axiomj.di.Binder;
import su.kidoz.axiomj.di.TestModule;
import su.kidoz.axiomj.property.Arbitrary;
import su.kidoz.axiomj.property.GenerationContext;
import su.kidoz.axiomj.property.GeneratorRegistry;

@ProductArea("Documentation")
@Feature(id = "docs.generators", name = "Generator Registry Examples", owner = "docs-team")
@UseModules(GeneratorRegistryExampleTest.MyGeneratorsModule.class)
public final class GeneratorRegistryExampleTest {

    // 1. Define a domain object
    record UserId(UUID value) {}

    // 2. Define a custom generator for the domain object
    public static final class UserIdGenerator implements Arbitrary<UserId> {
        @Override
        public UserId generate(GenerationContext context) {
            // For testing, we might want deterministic UUIDs based on the context random
            long mostSigBits = context.random().nextLong();
            long leastSigBits = context.random().nextLong();
            return new UserId(new UUID(mostSigBits, leastSigBits));
        }
    }

    // 3. Register the generator in a TestModule
    public static final class MyGeneratorsModule implements TestModule {
        @Override
        public void configure(Binder binder) {
            var registry = new GeneratorRegistry().register(UserId.class, new UserIdGenerator());

            // Bind the registry to the container so the engine can find it
            binder.bindInstance(GeneratorRegistry.class, registry);
        }
    }

    // 4. Use the domain object in a property test WITHOUT specifying gen = ...
    @Property(tries = 100)
    @Scenario("The engine automatically resolves the generator by type from the bound GeneratorRegistry")
    void customTypeIsGeneratedImplicitly(@ForAll UserId id) {
        expect(id).isNotNull();
        expect(id.value()).isNotNull();
    }

    @Property(tries = 50)
    @Scenario("Multiple parameters are resolved simultaneously")
    void multipleParametersResolved(@ForAll UserId id1, @ForAll UserId id2) {
        expect(id1).isNotNull();
        expect(id2).isNotNull();
        // The probability of a collision with 128 bits of randomness is astronomically low
        expect(id1).isNotEqualTo(id2);
    }
}
