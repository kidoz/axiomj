package su.kidoz.axiomj.engine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record RunConfig(
        List<String> classNames,
        Path jsonReport,
        Path markdownReport,
        Path allureResultsDir,
        Path junitXmlReport,
        Path sarifReport,
        Path htmlReport,
        long seed,
        int parallelism,
        boolean sequential,
        boolean failFast,
        boolean scanClasspath,
        List<String> includePackages,
        List<String> excludePackages,
        List<String> featureFilters,
        List<String> tagFilters,
        List<String> ownerFilters,
        List<String> areaFilters,
        List<String> requirementFilters,
        List<String> activeProfiles,
        boolean help) {

    /** Returns a copy with {@code classNames} replaced, leaving every other field untouched. */
    public RunConfig withClassNames(List<String> classNames) {
        return new RunConfig(
                List.copyOf(classNames),
                jsonReport,
                markdownReport,
                allureResultsDir,
                junitXmlReport,
                sarifReport,
                htmlReport,
                seed,
                parallelism,
                sequential,
                failFast,
                scanClasspath,
                includePackages,
                excludePackages,
                featureFilters,
                tagFilters,
                ownerFilters,
                areaFilters,
                requirementFilters,
                activeProfiles,
                help);
    }

    static RunConfig parse(String[] args) {
        var classes = new ArrayList<String>();
        var includePackages = new ArrayList<String>();
        var excludePackages = new ArrayList<String>();
        var activeProfiles = new ArrayList<String>();
        var features = new ArrayList<String>();
        var tags = new ArrayList<String>();
        var owners = new ArrayList<String>();
        var areas = new ArrayList<String>();
        var requirements = new ArrayList<String>();
        Path json = null;
        Path markdown = null;
        Path allure = null;
        Path junitXml = null;
        Path sarif = null;
        Path html = null;
        long seed = System.nanoTime();
        int parallelism = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 8));
        boolean sequential = false;
        boolean failFast = false;
        boolean scanClasspath = false;
        boolean help = false;
        for (String arg : args) {
            if (arg.startsWith("--json=")) {
                json = Path.of(arg.substring("--json=".length()));
            } else if (arg.startsWith("--markdown=")) {
                markdown = Path.of(arg.substring("--markdown=".length()));
            } else if (arg.startsWith("--md=")) {
                markdown = Path.of(arg.substring("--md=".length()));
            } else if (arg.startsWith("--allure-results=")) {
                allure = Path.of(arg.substring("--allure-results=".length()));
            } else if (arg.startsWith("--allure=")) {
                allure = Path.of(arg.substring("--allure=".length()));
            } else if (arg.startsWith("--junit-xml=")) {
                junitXml = Path.of(arg.substring("--junit-xml=".length()));
            } else if (arg.startsWith("--sarif=")) {
                sarif = Path.of(arg.substring("--sarif=".length()));
            } else if (arg.startsWith("--html=")) {
                html = Path.of(arg.substring("--html=".length()));
            } else if (arg.startsWith("--report=")) {
                String spec = arg.substring("--report=".length());
                int separator = spec.indexOf(':');
                if (separator <= 0 || separator == spec.length() - 1) {
                    throw new IllegalArgumentException(
                            "Expected --report=<json|markdown|md|allure|junit-xml|sarif|html>:<path>");
                }
                String type = spec.substring(0, separator).trim().toLowerCase();
                Path path = Path.of(spec.substring(separator + 1));
                switch (type) {
                    case "json" -> json = path;
                    case "markdown", "md" -> markdown = path;
                    case "allure" -> allure = path;
                    case "junit-xml" -> junitXml = path;
                    case "sarif" -> sarif = path;
                    case "html" -> html = path;
                    default -> throw new IllegalArgumentException("Unsupported report type: " + type);
                }
            } else if (arg.startsWith("--seed=")) {
                seed = Long.parseLong(arg.substring("--seed=".length()));
            } else if (arg.startsWith("--parallelism=")) {
                parallelism = Math.max(1, Integer.parseInt(arg.substring("--parallelism=".length())));
            } else if (arg.equals("--sequential")) {
                sequential = true;
                parallelism = 1;
            } else if (arg.equals("--fail-fast")) {
                failFast = true;
            } else if (arg.equals("--scan-classpath")) {
                scanClasspath = true;
            } else if (arg.startsWith("--include-package=")) {
                includePackages.add(arg.substring("--include-package=".length()));
            } else if (arg.startsWith("--exclude-package=")) {
                excludePackages.add(arg.substring("--exclude-package=".length()));
            } else if (arg.startsWith("--profile=")) {
                activeProfiles.add(arg.substring("--profile=".length()));
            } else if (arg.startsWith("--execution=")) {
                String mode = arg.substring("--execution=".length()).trim().toLowerCase();
                switch (mode) {
                    case "sequential" -> {
                        sequential = true;
                        parallelism = 1;
                    }
                    case "concurrent", "parallel" -> sequential = false;
                    default -> throw new IllegalArgumentException("Unsupported execution mode: " + mode);
                }
            } else if (arg.startsWith("--feature=")) {
                features.add(arg.substring("--feature=".length()));
            } else if (arg.startsWith("--tag=")) {
                tags.add(arg.substring("--tag=".length()));
            } else if (arg.startsWith("--owner=")) {
                owners.add(arg.substring("--owner=".length()));
            } else if (arg.startsWith("--area=")) {
                areas.add(arg.substring("--area=".length()));
            } else if (arg.startsWith("--requirement=")) {
                requirements.add(arg.substring("--requirement=".length()));
            } else if (arg.equals("--help") || arg.equals("-h")) {
                help = true;
            } else if (arg.startsWith("--")) {
                throw new IllegalArgumentException("Unknown option: " + arg + " (use --help to list options)");
            } else {
                classes.add(arg);
            }
        }
        return new RunConfig(
                List.copyOf(classes),
                json,
                markdown,
                allure,
                junitXml,
                sarif,
                html,
                seed,
                parallelism,
                sequential,
                failFast,
                scanClasspath,
                List.copyOf(includePackages),
                List.copyOf(excludePackages),
                List.copyOf(features),
                List.copyOf(tags),
                List.copyOf(owners),
                List.copyOf(areas),
                List.copyOf(requirements),
                List.copyOf(activeProfiles),
                help);
    }
}
