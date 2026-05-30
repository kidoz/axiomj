package su.kidoz.axiomj.property;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A registry for custom property-testing generators.
 *
 * <p>Bind an instance of this registry in a {@code TestModule} to allow {@code @ForAll} parameters to be resolved by
 * their element type without needing to explicitly declare {@code @ForAll(gen = ...)}.
 */
public final class GeneratorRegistry {
    private final Map<Type, Arbitrary<?>> registry = new ConcurrentHashMap<>();

    /** Registers a custom arbitrary for a specific class. */
    public <T> GeneratorRegistry register(Class<T> type, Arbitrary<? extends T> arbitrary) {
        registry.put(type, arbitrary);
        return this;
    }

    /** Registers a custom arbitrary for a specific generic type. */
    public GeneratorRegistry register(Type type, Arbitrary<?> arbitrary) {
        registry.put(type, arbitrary);
        return this;
    }

    /**
     * Retrieves a registered arbitrary for the given type and generic type, or {@code null} if none is found. It
     * prefers an exact match on {@code genericType}, falling back to {@code type}.
     */
    @SuppressWarnings("unchecked")
    public <T> Arbitrary<T> get(Class<T> type, Type genericType) {
        var exact = registry.get(genericType != null ? genericType : type);
        if (exact != null) {
            return (Arbitrary<T>) exact;
        }
        return (Arbitrary<T>) registry.get(type);
    }
}
