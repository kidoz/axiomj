package su.kidoz.axiomj.engine;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        var config = RunConfig.parse(args);

        java.util.List<String> classesToRun = new java.util.ArrayList<>(config.classNames());
        if (config.scanClasspath()) {
            classesToRun.addAll(ClasspathScanner.scan(config));
        }

        if (config.help()) {
            printUsage();
            return;
        }

        // Selecting no tests is a configuration error, not a success: exiting 0 here would let a
        // misconfigured runner/plugin report a green build while executing nothing.
        if (classesToRun.isEmpty()) {
            System.err.println(
                    "AxiomJ: no tests were selected. "
                            + "Pass one or more test classes, or use --scan-classpath (optionally with --include-package=...).");
            printUsage();
            System.exit(2);
        }

        var effectiveConfig = config.withClassNames(classesToRun);

        var summary = new TestRunner(System.out).run(effectiveConfig);
        if (summary.failed() > 0) {
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("AxiomJ Test");
        System.out.println("Usage:");
        System.out.println("  java -cp <classes> su.kidoz.axiomj.engine.Main [options] <test-class>...");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --json=path          Write AI-readable JSON report");
        System.out.println("  --markdown=path      Write AI-readable Markdown report");
        System.out.println("  --allure-results=dir Write Allure-compatible result files to dir");
        System.out.println("  --junit-xml=path     Write JUnit XML report");
        System.out.println("  --sarif=path         Write SARIF report");
        System.out.println("  --html=path          Write HTML report");
        System.out.println(
                "  --report=type:path   Generic report syntax: json, markdown/md, allure, junit-xml, sarif, or html");
        System.out.println("  --seed=long          Set run seed for generated properties");
        System.out.println("  --parallelism=N      Run independent tests concurrently with N virtual workers");
        System.out.println("  --sequential         Alias for --parallelism=1");
        System.out.println("  --fail-fast          Abort the test run on the first failure");
        System.out.println("  --scan-classpath     Scan the classpath for tests");
        System.out.println("  --include-package=P  Only include tests in package P (used with --scan-classpath)");
        System.out.println("  --exclude-package=P  Exclude tests in package P (used with --scan-classpath)");
        System.out.println("  --execution=mode     sequential, concurrent, or parallel");
        System.out.println("  --feature=id         Run only matching feature id/name/area; supports trailing *");
        System.out.println("  --tag=name           Run only matching tags");
        System.out.println("  --owner=name         Run only matching owners");
        System.out.println("  --area=name          Run only matching product areas");
        System.out.println("  --requirement=id     Run only matching requirements");
    }
}
