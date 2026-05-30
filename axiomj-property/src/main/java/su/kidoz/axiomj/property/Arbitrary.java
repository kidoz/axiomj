package su.kidoz.axiomj.property;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface Arbitrary<T> {
    T generate(GenerationContext context);

    default List<T> shrink(T value) {
        return List.of();
    }

    default <U> Arbitrary<U> map(Function<T, U> mapper) {
        var self = this;
        return new Arbitrary<U>() {
            @Override
            public U generate(GenerationContext context) {
                return mapper.apply(self.generate(context));
            }
        };
    }

    default Arbitrary<T> filter(Predicate<T> predicate) {
        var self = this;
        return new Arbitrary<T>() {
            @Override
            public T generate(GenerationContext context) {
                for (int i = 0; i < 1000; i++) {
                    T value = self.generate(context);
                    if (predicate.test(value)) {
                        return value;
                    }
                }
                throw new IllegalStateException(
                        "Failed to generate a value satisfying the filter after 1000 attempts.");
            }

            @Override
            public List<T> shrink(T value) {
                return self.shrink(value).stream().filter(predicate).collect(Collectors.toList());
            }
        };
    }

    default <U> Arbitrary<U> flatMap(Function<T, Arbitrary<U>> mapper) {
        var self = this;
        return new Arbitrary<U>() {
            @Override
            public U generate(GenerationContext context) {
                T inner = self.generate(context);
                return mapper.apply(inner).generate(context);
            }
        };
    }
}
