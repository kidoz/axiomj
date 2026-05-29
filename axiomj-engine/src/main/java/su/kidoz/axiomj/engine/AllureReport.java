package su.kidoz.axiomj.engine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class AllureReport {
    private AllureReport() {}

    static void write(Path resultsDir, List<TestResult> results) throws java.io.IOException {
        Files.createDirectories(resultsDir);
        clean(resultsDir);
        for (var result : results) {
            var uuid = uuid(result.id() + ":" + result.startMillis());
            Files.writeString(
                    resultsDir.resolve(uuid + "-result.json"), renderResult(uuid, result), StandardCharsets.UTF_8);
        }
        Files.writeString(resultsDir.resolve("environment.properties"), environment(), StandardCharsets.UTF_8);
    }

    // Allure renders an Environment widget from a properties file in the results directory.
    private static String environment() {
        var env = new StringBuilder();
        env.append("framework=axiomj\n");
        env.append("java.version=")
                .append(System.getProperty("java.version", ""))
                .append('\n');
        env.append("java.vendor=").append(System.getProperty("java.vendor", "")).append('\n');
        env.append("os.name=").append(System.getProperty("os.name", "")).append('\n');
        env.append("os.arch=").append(System.getProperty("os.arch", "")).append('\n');
        env.append("os.version=").append(System.getProperty("os.version", "")).append('\n');
        return env.toString();
    }

    // The result UUID embeds startMillis, so every run emits new filenames. Remove the Allure
    // artifacts from previous runs first so results do not accumulate across invocations.
    private static void clean(Path resultsDir) throws java.io.IOException {
        try (var entries = Files.newDirectoryStream(resultsDir)) {
            for (var entry : entries) {
                var name = entry.getFileName().toString();
                if (name.endsWith("-result.json")
                        || name.endsWith("-container.json")
                        || name.endsWith("-attachment")
                        || name.contains("-attachment.")) {
                    Files.deleteIfExists(entry);
                }
            }
        }
    }

    private static String renderResult(String uuid, TestResult result) {
        var json = new StringBuilder();
        var labels = labels(result);
        json.append("{\n");
        field(json, "uuid", uuid).append(",\n");
        field(json, "historyId", uuid(result.id())).append(",\n");
        field(json, "testCaseId", uuid(result.className() + "#" + result.methodName()))
                .append(",\n");
        field(json, "fullName", result.id()).append(",\n");
        field(json, "name", result.displayName()).append(",\n");
        field(json, "description", description(result)).append(",\n");
        json.append("  \"status\": ");
        quote(json, allureStatus(result)).append(",\n");
        if (result.error() != null) {
            json.append("  \"statusDetails\": {");
            fieldInline(json, "message", String.valueOf(result.error().getMessage()))
                    .append(", ");
            fieldInline(json, "trace", stackTrace(result.error()));
            json.append("},\n");
        }
        json.append("  \"stage\": \"finished\",\n");
        json.append("  \"start\": ").append(result.startMillis()).append(",\n");
        json.append("  \"stop\": ").append(result.stopMillis()).append(",\n");
        json.append("  \"labels\": [\n");
        for (int i = 0; i < labels.size(); i++) {
            var label = labels.get(i);
            json.append("    {");
            fieldInline(json, "name", label.name()).append(", ");
            fieldInline(json, "value", label.value());
            json.append("}");
            if (i < labels.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("  ],\n");
        json.append("  \"links\": [],\n");
        json.append("  \"parameters\": [],\n");
        json.append("  \"steps\": [],\n");
        json.append("  \"attachments\": []\n");
        json.append("}\n");
        return json.toString();
    }

    private static List<Label> labels(TestResult result) {
        var labels = new ArrayList<Label>();
        add(labels, "language", "java");
        add(labels, "framework", "axiomj");
        add(labels, "suite", result.className());
        add(labels, "package", packageName(result.className()));
        add(labels, "testClass", simpleName(result.className()));
        add(labels, "testMethod", result.methodName());
        add(labels, "source", result.sourceFile());
        add(labels, "thread", String.valueOf(result.metadata().getOrDefault("thread", "")));
        add(labels, "epic", result.productArea());
        add(labels, "feature", result.featureLabel());
        add(labels, "story", result.scenario());
        add(labels, "owner", result.owner());
        for (var requirement : result.requirements()) add(labels, "requirement", requirement);
        for (var tag : result.tags()) add(labels, "tag", tag);
        return labels;
    }

    private static void add(List<Label> labels, String name, String value) {
        if (value != null && !value.isBlank()) labels.add(new Label(name, value));
    }

    private static String description(TestResult result) {
        var text = new StringBuilder();
        text.append("Source: ").append(result.sourceFile());
        if (!result.productArea().isBlank()) text.append("\nArea: ").append(result.productArea());
        if (!result.featureId().isBlank()) text.append("\nFeature ID: ").append(result.featureId());
        if (!result.scenario().isBlank()) text.append("\nScenario: ").append(result.scenario());
        if (!result.dependsOn().isEmpty()) text.append("\nDepends on: ").append(String.join(", ", result.dependsOn()));
        if (!result.requirements().isEmpty())
            text.append("\nRequirements: ").append(String.join(", ", result.requirements()));
        return text.toString();
    }

    private static String allureStatus(TestResult result) {
        return switch (result.status()) {
            case PASSED -> "passed";
            case FAILED -> "failed";
            case SKIPPED -> "skipped";
        };
    }

    private static String packageName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? "" : className.substring(0, dot);
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    private static String uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String stackTrace(Throwable error) {
        var sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static StringBuilder field(StringBuilder json, String name, String value) {
        json.append("  ");
        return fieldInline(json, name, value);
    }

    private static StringBuilder fieldInline(StringBuilder json, String name, String value) {
        quote(json, name).append(": ");
        quote(json, value);
        return json;
    }

    private static StringBuilder quote(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) json.append("\\u%04x".formatted((int) c));
                    else json.append(c);
                }
            }
        }
        json.append('"');
        return json;
    }

    private record Label(String name, String value) {}
}
