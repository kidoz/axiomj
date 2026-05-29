package su.kidoz.axiomj.property;

import java.util.SplittableRandom;

public record GenerationContext(SplittableRandom random, long seed, int attempt) {}
