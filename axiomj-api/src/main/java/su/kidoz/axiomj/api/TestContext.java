package su.kidoz.axiomj.api;

public record TestContext(
        String id,
        String displayName,
        long seed,
        int attempt,
        boolean propertyInvocation,
        String productArea,
        String featureId,
        String featureName,
        String scenario) {
    public TestContext(String id, String displayName, long seed, int attempt, boolean propertyInvocation) {
        this(id, displayName, seed, attempt, propertyInvocation, "", "", "", "");
    }
}
