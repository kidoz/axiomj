package su.kidoz.axiomj.engine;

import java.util.List;

public record TestMetadata(
        String productArea,
        String featureId,
        String featureName,
        String capability,
        String scenario,
        String owner,
        List<String> requirements) {
    public static TestMetadata empty() {
        return new TestMetadata("", "", "", "", "", "", List.of());
    }
}
