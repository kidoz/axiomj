package su.kidoz.axiomj.property;

import java.util.SplittableRandom;

public record GenerationContext(SplittableRandom random, long seed, int attempt, int size) {
    // Sized generators grow structures with the attempt index (small inputs first, capped at 100) so a property
    // explores simple cases early and larger ones later; override explicitly with withSize(...).
    public GenerationContext(SplittableRandom random, long seed, int attempt) {
        this(random, seed, attempt, Math.min(attempt, 100));
    }

    public GenerationContext withSize(int newSize) {
        return new GenerationContext(random, seed, attempt, newSize);
    }
}
