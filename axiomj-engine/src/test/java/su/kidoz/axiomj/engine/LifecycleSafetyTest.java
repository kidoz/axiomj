package su.kidoz.axiomj.engine;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import su.kidoz.axiomj.api.AfterEach;
import su.kidoz.axiomj.api.BeforeSuite;
import su.kidoz.axiomj.api.Fact;

/** Dogfood: AxiomJ verifies its own runner is library-safe — no System.exit, and cleanup never masks failures. */
class LifecycleSafetyTest {

    static class BeforeSuiteFails {
        @BeforeSuite
        static void setup() {
            throw new IllegalStateException("suite setup boom");
        }

        @Fact
        void neverRuns() {}
    }

    static class CleanupAlsoFails {
        @Fact
        void bodyFails() {
            throw new IllegalStateException("primary failure");
        }

        @AfterEach
        void cleanup() {
            throw new IllegalStateException("cleanup failure");
        }
    }

    @Fact(name = "a @BeforeSuite failure becomes a result instead of killing the JVM")
    void beforeSuiteFailureIsRecorded() throws Exception {
        var runner = new TestRunner(new PrintStream(new ByteArrayOutputStream()));
        var config = RunConfig.parse(new String[] {"--sequential", BeforeSuiteFails.class.getName()});

        var summary = runner.run(config); // reaching this line at all proves run() did not call System.exit

        expect(summary.failed() >= 1).isTrue();
    }

    @Fact(name = "an @AfterEach failure does not mask the primary test failure")
    void afterEachDoesNotMaskPrimaryFailure() throws Exception {
        var runner = new TestRunner(new PrintStream(new ByteArrayOutputStream()));
        Path json = Files.createTempFile("suppressed-report", ".json");
        try {
            var config =
                    RunConfig.parse(new String[] {"--sequential", "--json=" + json, CleanupAlsoFails.class.getName()});
            runner.run(config);

            var report = Files.readString(json);
            expect(report.contains("primary failure")).isTrue(); // the real cause is preserved...
            expect(report.contains("Suppressed")).isTrue(); // ...and cleanup is attached as suppressed
            expect(report.contains("cleanup failure")).isTrue();
        } finally {
            Files.deleteIfExists(json);
        }
    }
}
