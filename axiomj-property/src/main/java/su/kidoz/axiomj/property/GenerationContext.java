package su.kidoz.axiomj.property;

import java.util.SplittableRandom;

public record GenerationContext(SplittableRandom random, long seed, int attempt, int size) {
    public GenerationContext(SplittableRandom random, long seed, int attempt) {
        this(random, seed, attempt, Math.min(attempt, 100));
    }

    public GenerationContext withSize(int newSize) {
        return new GenerationContext(random, seed, attempt, newSize);
    }
}
