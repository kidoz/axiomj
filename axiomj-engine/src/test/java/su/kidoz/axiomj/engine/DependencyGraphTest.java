package su.kidoz.axiomj.engine;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import su.kidoz.axiomj.api.Fact;

class DependencyGraphTest {

    static class CycleTest {
        @Fact(dependsOn = "b")
        void a() {}

        @Fact(dependsOn = "c")
        void b() {}

        @Fact(dependsOn = "a")
        void c() {}
    }

    static class UnknownDependencyTest {
        @Fact(dependsOn = "missing")
        void a() {}
    }

    @Fact
    void detectsDependencyCycle() throws Exception {
        var out = new ByteArrayOutputStream();
        var runner = new TestRunner(new PrintStream(out));
        Path jsonPath = Files.createTempFile("cycle-report", ".json");
        try {
            var config = new RunConfig(
                    List.<String>of(CycleTest.class.getName()),
                    jsonPath,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    1,
                    true,
                    false,
                    false,
                    List.<String>of(),
                    List.<String>of(),
                    List.<String>of(),
                    List.<String>of(),
                    List.<String>of(),
                    List.<String>of(),
                    List.<String>of(),
                    List.<String>of(),
                    false);
            var summary = runner.run(config);
            expect(summary.total()).isEqualTo(3);
            expect(summary.passed()).isEqualTo(0);
            expect(summary.failed()).isEqualTo(3);

            String jsonContent = Files.readString(jsonPath);
            expect(jsonContent).contains("Dependency cycle or unresolved sequence involving");
        } finally {
            Files.deleteIfExists(jsonPath);
        }
    }

    @Fact
    void detectsUnknownDependency() throws Exception {
        var out = new ByteArrayOutputStream();
        var runner = new TestRunner(new PrintStream(out));
        Path jsonPath = Files.createTempFile("unknown-dep-report", ".json");
        try {
            var config = new RunConfig(
                    List.<String>of(UnknownDependencyTest.class.getName()),
                    jsonPath,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    1,
                    true,
                    false,
                    false,
                    List.<String>of(),
                    List.<String>of(),
                    List.<String>of(),
                    List.<String>of(),
                    List.<String>of(),
                    List.<String>of(),
                    List.<String>of(),
                    List.<String>of(),
                    false);
            var summary = runner.run(config);
            expect(summary.total()).isEqualTo(1);
            expect(summary.passed()).isEqualTo(0);
            expect(summary.failed()).isEqualTo(1);

            String jsonContent = Files.readString(jsonPath);
            expect(jsonContent).contains("Unknown dependency 'missing' for test a");
        } finally {
            Files.deleteIfExists(jsonPath);
        }
    }
}
