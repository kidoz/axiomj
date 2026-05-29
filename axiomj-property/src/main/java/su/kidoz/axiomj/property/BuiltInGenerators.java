package su.kidoz.axiomj.property;

import java.lang.annotation.Annotation;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.SplittableRandom;
import su.kidoz.axiomj.api.IntRange;
import su.kidoz.axiomj.api.LongRange;
import su.kidoz.axiomj.api.StringLength;

public final class BuiltInGenerators {
    private BuiltInGenerators() {}

    public static Object generate(Class<?> rawType, Annotation[] annotations, GenerationContext context) {
        var random = context.random();
        if (rawType == int.class || rawType == Integer.class) {
            var range = find(annotations, IntRange.class);
            int min = range == null ? -1000 : range.min();
            int max = range == null ? 1000 : range.max();
            if (max < min) throw new IllegalArgumentException("@IntRange max must be >= min");
            long span = (long) max - min + 1L;
            return (int) (min + random.nextLong(span));
        }
        if (rawType == long.class || rawType == Long.class) {
            var range = find(annotations, LongRange.class);
            long min = range == null ? -1_000_000L : range.min();
            long max = range == null ? 1_000_000L : range.max();
            if (max < min) throw new IllegalArgumentException("@LongRange max must be >= min");
            return nextLongInclusive(random, min, max);
        }
        if (rawType == boolean.class || rawType == Boolean.class) {
            return random.nextBoolean();
        }
        if (rawType == double.class || rawType == Double.class) {
            return random.nextDouble(-1_000d, 1_000d);
        }
        if (rawType == String.class) {
            var length = find(annotations, StringLength.class);
            int min = length == null ? 0 : length.min();
            int max = length == null ? 32 : length.max();
            if (max < min) throw new IllegalArgumentException("@StringLength max must be >= min");
            int size = min == max ? min : random.nextInt(min, max + 1);
            var builder = new StringBuilder(size);
            var alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            for (int i = 0; i < size; i++) {
                builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            return builder.toString();
        }
        if (rawType.isEnum()) {
            var constants = rawType.getEnumConstants();
            return constants[random.nextInt(constants.length)];
        }
        if (rawType.isRecord()) {
            return generateRecord(rawType, context);
        }
        throw new IllegalArgumentException("No built-in generator for " + rawType.getName());
    }

    private static Object generateRecord(Class<?> rawType, GenerationContext context) {
        try {
            RecordComponent[] components = rawType.getRecordComponents();
            var parameterTypes =
                    Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
            var args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                args[i] = generate(components[i].getType(), components[i].getAnnotations(), context);
            }
            var constructor = rawType.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not generate record " + rawType.getName(), e);
        }
    }

    // Uniform draw over the inclusive range [min, max]. SplittableRandom only offers an
    // exclusive upper bound, so naively computing max + 1L overflows when max == Long.MAX_VALUE
    // (e.g. @LongRange(min = 0, max = Long.MAX_VALUE) wrapped to bound = Long.MIN_VALUE).
    private static long nextLongInclusive(SplittableRandom random, long min, long max) {
        if (min == Long.MIN_VALUE && max == Long.MAX_VALUE) {
            return random.nextLong();
        }
        if (max == Long.MAX_VALUE) {
            // min > Long.MIN_VALUE here, so min - 1L cannot underflow. Sampling [min - 1, max)
            // and shifting up by one yields a uniform draw over [min, Long.MAX_VALUE].
            return random.nextLong(min - 1L, max) + 1L;
        }
        return random.nextLong(min, max + 1L);
    }

    private static <A extends Annotation> A find(Annotation[] annotations, Class<A> type) {
        for (var annotation : annotations) {
            if (type.isInstance(annotation)) {
                return type.cast(annotation);
            }
        }
        return null;
    }
}
