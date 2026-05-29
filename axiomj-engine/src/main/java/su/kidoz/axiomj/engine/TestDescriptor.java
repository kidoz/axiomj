package su.kidoz.axiomj.engine;

import java.util.List;

public record TestDescriptor(
        String id,
        String className,
        String methodName,
        String displayName,
        List<String> tags,
        String productArea,
        String featureId,
        String featureName,
        String owner,
        String scenario,
        List<String> requirements,
        SourceLocation source,
        List<String> dependsOn,
        int order) {
    public String featureLabel() {
        if (featureName != null && !featureName.isBlank() && featureId != null && !featureId.isBlank())
            return featureId + " - " + featureName;
        if (featureName != null && !featureName.isBlank()) return featureName;
        if (featureId != null && !featureId.isBlank()) return featureId;
        return "Unassigned feature";
    }
}
