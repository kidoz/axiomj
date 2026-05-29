package su.kidoz.axiomj.engine;

import java.util.List;
import java.util.Map;

public record TestResult(
        TestDescriptor descriptor,
        TestStatus status,
        long startMillis,
        long stopMillis,
        long durationMillis,
        Throwable error,
        Map<String, Object> metadata) {
    public boolean passed() {
        return status == TestStatus.PASSED;
    }

    public boolean failed() {
        return status == TestStatus.FAILED;
    }

    public boolean skipped() {
        return status == TestStatus.SKIPPED;
    }

    public String id() {
        return descriptor.id();
    }

    public String className() {
        return descriptor.className();
    }

    public String methodName() {
        return descriptor.methodName();
    }

    public String displayName() {
        return descriptor.displayName();
    }

    public List<String> tags() {
        return descriptor.tags();
    }

    public String productArea() {
        return descriptor.productArea();
    }

    public String featureId() {
        return descriptor.featureId();
    }

    public String featureName() {
        return descriptor.featureName();
    }

    public String owner() {
        return descriptor.owner();
    }

    public String scenario() {
        return descriptor.scenario();
    }

    public List<String> requirements() {
        return descriptor.requirements();
    }

    public SourceLocation source() {
        return descriptor.source();
    }

    public String sourceFile() {
        return descriptor.source().file();
    }

    public List<String> dependsOn() {
        return descriptor.dependsOn();
    }

    public int order() {
        return descriptor.order();
    }

    public String featureLabel() {
        return descriptor.featureLabel();
    }

    public TestMetadata metadataInfo() {
        return new TestMetadata(productArea(), featureId(), featureName(), "", scenario(), owner(), requirements());
    }

    public long startEpochMillis() {
        return startMillis;
    }

    public long stopEpochMillis() {
        return stopMillis;
    }

    public String skipReason() {
        Object reason = metadata.get("skipReason");
        if (reason != null) return String.valueOf(reason);
        if (status == TestStatus.SKIPPED && error != null) return error.getMessage();
        return null;
    }
}
