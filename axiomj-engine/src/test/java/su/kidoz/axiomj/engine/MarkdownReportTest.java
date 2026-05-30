package su.kidoz.axiomj.engine;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import su.kidoz.axiomj.api.Fact;

class MarkdownReportTest {

    @Fact
    void rendersEmptyReport() {
        var summary = new RunSummary(0, 0, 0, 0, 100L);
        var config = new RunConfig(
                List.of(),
                null,
                Path.of("report.md"),
                null,
                null,
                null,
                null,
                42L,
                4,
                false,
                false,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false);

        String md = MarkdownReport.render(List.of(), summary, config);

        expect(md).contains("# AxiomJ Test Report");
        expect(md).contains("| 0 | 0 | 0 | 0 | 100 ms | 42 | 4 |");
        expect(md).contains("## Failures\n\nNone.");
        expect(md).contains("## Skipped tests\n\nNone.");
    }

    @Fact
    void rendersFailedAndSkippedTests() {
        var summary = new RunSummary(2, 0, 1, 1, 100L);
        var config = new RunConfig(
                List.of(),
                null,
                Path.of("report.md"),
                null,
                null,
                null,
                null,
                42L,
                4,
                false,
                false,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false);

        var loc = new SourceLocation("MyTest.java", 10, 10, 20);
        var desc1 = new TestDescriptor(
                "test1",
                "MyClass",
                "myMethod",
                "my display name",
                List.of("tag1"),
                "Area",
                "feature-1",
                "Feature One",
                "owner",
                "scenario",
                List.of("req1"),
                loc,
                List.of(),
                0);
        var failedResult = new TestResult(
                desc1,
                TestStatus.FAILED,
                0L,
                100L,
                100L,
                new RuntimeException("boom"),
                Map.of("tries", 50, "seed", 123L, "sample", "bad", "minimizedSample", "b"));

        var desc2 = new TestDescriptor(
                "test2",
                "MyClass",
                "skipMethod",
                "skip display",
                List.of(),
                "",
                "",
                "",
                "",
                "",
                List.of(),
                loc,
                List.of(),
                0);
        var skippedResult =
                new TestResult(desc2, TestStatus.SKIPPED, 0L, 0L, 0L, null, Map.of("skipReason", "ignored"));

        String md = MarkdownReport.render(List.of(failedResult, skippedResult), summary, config);

        expect(md).contains("### FAIL my display name");
        expect(md).contains("- Test: `MyClass#myMethod`");
        expect(md).contains("- Message: boom");
        expect(md).contains("- Failing sample: `bad`");

        expect(md).contains("### SKIP skip display");
        expect(md).contains("- Reason: ignored");
    }
}
