package su.kidoz.axiomj.engine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

final class MarkdownReport {
    private MarkdownReport() {}

    static String render(List<TestResult> results, RunSummary summary, RunConfig config) {
        var md = new StringBuilder();
        md.append("# AxiomJ Test Report\n\n");
        md.append("Generated: `").append(Instant.now()).append("`\n\n");
        md.append("## Summary\n\n");
        md.append("| Total | Passed | Failed | Skipped | Duration | Seed | Parallelism |\n");
        md.append("|---:|---:|---:|---:|---:|---:|---:|\n");
        md.append("| ")
                .append(summary.total())
                .append(" | ")
                .append(summary.passed())
                .append(" | ")
                .append(summary.failed())
                .append(" | ")
                .append(summary.skipped())
                .append(" | ")
                .append(summary.durationMillis())
                .append(" ms")
                .append(" | ")
                .append(config.seed())
                .append(" | ")
                .append(config.parallelism())
                .append(" |\n\n");

        md.append("## AI agent index\n\n");
        md.append(
                "Each row contains the Java class, method, source file guess, feature metadata, status, and dependency chain.\n\n");
        md.append("| Status | Feature | Scenario | Test | Source file | Duration | Depends on | Message |\n");
        md.append("|---|---|---|---|---|---:|---|---|\n");
        for (var result : sorted(results)) {
            md.append("| ")
                    .append(status(result))
                    .append(" | ")
                    .append(cell(featurePath(result)))
                    .append(" | ")
                    .append(cell(result.scenario()))
                    .append(" | `")
                    .append(result.className())
                    .append("#")
                    .append(result.methodName())
                    .append("`")
                    .append(" | `")
                    .append(result.sourceFile())
                    .append("`")
                    .append(" | ")
                    .append(result.durationMillis())
                    .append(" ms")
                    .append(" | ")
                    .append(cell(String.join(", ", result.dependsOn())))
                    .append(" | ")
                    .append(message(result))
                    .append(" |\n");
        }
        md.append("\n");

        var byFeature = new LinkedHashMap<String, List<TestResult>>();
        for (var result : sorted(results)) {
            byFeature
                    .computeIfAbsent(featurePath(result), _ -> new ArrayList<>())
                    .add(result);
        }
        md.append("## Feature groups\n\n");
        for (var entry : byFeature.entrySet()) {
            md.append("### ").append(entry.getKey()).append("\n\n");
            md.append("| Status | Scenario | Test | Requirements | Tags |\n");
            md.append("|---|---|---|---|---|\n");
            for (var result : entry.getValue()) {
                md.append("| ")
                        .append(status(result))
                        .append(" | ")
                        .append(cell(result.scenario()))
                        .append(" | `")
                        .append(result.methodName())
                        .append("`")
                        .append(" | ")
                        .append(cell(String.join(", ", result.requirements())))
                        .append(" | ")
                        .append(cell(String.join(", ", result.tags())))
                        .append(" |\n");
            }
            md.append("\n");
        }

        var failures = results.stream().filter(TestResult::failed).toList();
        md.append("## Failures\n\n");
        if (failures.isEmpty()) {
            md.append("None.\n\n");
        } else {
            for (var result : failures) {
                detailBlock(md, result);
            }
        }

        var skips = results.stream().filter(TestResult::skipped).toList();
        md.append("## Skipped tests\n\n");
        if (skips.isEmpty()) {
            md.append("None.\n\n");
        } else {
            for (var result : skips) {
                md.append("### SKIP ").append(result.displayName()).append("\n\n");
                md.append("- Test: `")
                        .append(result.className())
                        .append("#")
                        .append(result.methodName())
                        .append("`\n");
                md.append("- Source: `").append(result.sourceFile()).append("`\n");
                md.append("- Feature: ").append(featurePath(result)).append("\n");
                var reason = result.skipReason();
                if (reason != null)
                    md.append("- Reason: ").append(inline(reason)).append("\n");
                md.append("\n");
            }
        }

        var properties =
                sorted(results).stream().filter(MarkdownReport::isProperty).toList();
        md.append("## Property samples\n\n");
        if (properties.isEmpty()) {
            md.append("No property tests in this run.\n\n");
        } else {
            md.append("| Status | Test | Seed | Tries | Failing sample | Minimized sample |\n");
            md.append("|---|---|---:|---:|---|---|\n");
            for (var result : properties) {
                md.append("| ")
                        .append(status(result))
                        .append(" | `")
                        .append(result.className())
                        .append("#")
                        .append(result.methodName())
                        .append("`")
                        .append(" | ")
                        .append(meta(result, "seed"))
                        .append(" | ")
                        .append(meta(result, "tries"))
                        .append(" | ")
                        .append(cell(meta(result, "sample")))
                        .append(" | ")
                        .append(cell(meta(result, "minimizedSample")))
                        .append(" |\n");
            }
            md.append("\n");
        }

        md.append("## Environment\n\n");
        md.append("| Property | Value |\n");
        md.append("|---|---|\n");
        md.append("| Framework | AxiomJ |\n");
        md.append("| Seed | ").append(config.seed()).append(" |\n");
        md.append("| Parallelism | ").append(config.parallelism()).append(" |\n");
        md.append("| Java version | ")
                .append(cell(System.getProperty("java.version", "")))
                .append(" |\n");
        md.append("| OS | ")
                .append(cell(System.getProperty("os.name", "") + " " + System.getProperty("os.version", "")))
                .append(" |\n");
        md.append("\n");
        return md.toString();
    }

    private static void detailBlock(StringBuilder md, TestResult result) {
        md.append("### ")
                .append(status(result))
                .append(" ")
                .append(result.displayName())
                .append("\n\n");
        md.append("- Test: `")
                .append(result.className())
                .append("#")
                .append(result.methodName())
                .append("`\n");
        md.append("- Source: `").append(result.sourceFile()).append("`\n");
        md.append("- Feature: ").append(featurePath(result)).append("\n");
        if (!result.dependsOn().isEmpty())
            md.append("- Depends on: `")
                    .append(String.join("`, `", result.dependsOn()))
                    .append("`\n");
        if (isProperty(result)) {
            md.append("- Seed: `").append(meta(result, "seed")).append("`\n");
            if (!meta(result, "sample").isEmpty())
                md.append("- Failing sample: `").append(meta(result, "sample")).append("`\n");
            if (!meta(result, "minimizedSample").isEmpty())
                md.append("- Minimized sample: `")
                        .append(meta(result, "minimizedSample"))
                        .append("`\n");
        }
        if (result.error() != null) {
            md.append("- Message: ").append(inline(result.error().getMessage())).append("\n\n");
            md.append("```text\n").append(stackTraceHead(result.error(), 40)).append("```\n\n");
        } else md.append("\n");
    }

    private static boolean isProperty(TestResult result) {
        return result.metadata().containsKey("tries");
    }

    private static String meta(TestResult result, String key) {
        var value = result.metadata().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static List<TestResult> sorted(List<TestResult> results) {
        return results.stream()
                .sorted(Comparator.comparing(TestResult::productArea)
                        .thenComparing(TestResult::featureId)
                        .thenComparing(TestResult::className)
                        .thenComparingInt(TestResult::order)
                        .thenComparing(TestResult::methodName))
                .toList();
    }

    private static String featurePath(TestResult result) {
        var area = result.productArea().isBlank() ? "No area" : result.productArea();
        return area + " / " + result.featureLabel();
    }

    private static String status(TestResult result) {
        return switch (result.status()) {
            case PASSED -> "PASS";
            case FAILED -> "FAIL";
            case SKIPPED -> "SKIP";
        };
    }

    private static String message(TestResult r) {
        return r.error() == null
                ? ""
                : cell(r.error().getClass().getSimpleName() + ": "
                        + inline(r.error().getMessage()));
    }

    private static String cell(String value) {
        if (value == null || value.isBlank()) return "";
        return value.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
    }

    private static String inline(String value) {
        if (value == null || value.isBlank()) return "";
        return value.replace("\n", " ").replace("\r", " ");
    }

    private static String stackTraceHead(Throwable error, int maxLines) {
        var trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        var lines = trace.toString().split("\\R");
        var out = new StringBuilder();
        for (int i = 0; i < Math.min(maxLines, lines.length); i++)
            out.append(lines[i]).append('\n');
        if (lines.length > maxLines) out.append("... truncated ...\n");
        return out.toString();
    }
}
