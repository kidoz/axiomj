package su.kidoz.axiomj.engine;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        var config = RunConfig.parse(args);
        if (config.help() || config.classNames().isEmpty()) {
            printUsage();
            return;
        }
        var summary = new TestRunner(System.out).run(config);
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
        System.out.println("  --report=type:path   Generic report syntax: json, markdown/md, or allure");
        System.out.println("  --seed=long          Set run seed for generated properties");
        System.out.println("  --parallelism=N      Run independent tests concurrently with N virtual workers");
        System.out.println("  --sequential         Alias for --parallelism=1");
        System.out.println("  --execution=mode     sequential, concurrent, or parallel");
        System.out.println("  --feature=id         Run only matching feature id/name/area; supports trailing *");
    }
}
