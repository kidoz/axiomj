package su.kidoz.axiomj.engine;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.nio.file.Path;
import su.kidoz.axiomj.api.Fact;

class RunConfigTest {

    @Fact
    void parsesDefaultArguments() {
        var config = RunConfig.parse(new String[0]);
        expect(config.classNames().isEmpty()).isTrue();
        expect(config.jsonReport() == null).isTrue();
        expect(config.markdownReport() == null).isTrue();
        expect(config.allureResultsDir() == null).isTrue();
        expect(config.featureFilters().isEmpty()).isTrue();
        expect(config.help()).isFalse();
        expect(config.sequential()).isFalse();
    }

    @Fact
    void parsesClassNames() {
        var config = RunConfig.parse(new String[] {"examples.Test1", "examples.Test2"});
        expect(config.classNames().size()).isEqualTo(2);
        expect(config.classNames().get(0)).isEqualTo("examples.Test1");
        expect(config.classNames().get(1)).isEqualTo("examples.Test2");
    }

    @Fact
    void parsesLegacyReportPaths() {
        var config = RunConfig.parse(new String[] {
            "--json=build/report.json", "--markdown=build/report.md", "--allure-results=build/allure-results"
        });
        expect(config.jsonReport()).isEqualTo(Path.of("build/report.json"));
        expect(config.markdownReport()).isEqualTo(Path.of("build/report.md"));
        expect(config.allureResultsDir()).isEqualTo(Path.of("build/allure-results"));
    }

    @Fact
    void parsesMdAndAllureAliases() {
        var config = RunConfig.parse(new String[] {"--md=build/alias.md", "--allure=build/alias-allure"});
        expect(config.markdownReport()).isEqualTo(Path.of("build/alias.md"));
        expect(config.allureResultsDir()).isEqualTo(Path.of("build/alias-allure"));
    }

    @Fact
    void parsesReportOption() {
        var config = RunConfig.parse(
                new String[] {"--report=json:build/r.json", "--report=md:build/r.md", "--report=allure:build/r-allure"
                });
        expect(config.jsonReport()).isEqualTo(Path.of("build/r.json"));
        expect(config.markdownReport()).isEqualTo(Path.of("build/r.md"));
        expect(config.allureResultsDir()).isEqualTo(Path.of("build/r-allure"));
    }

    @Fact
    void throwsOnInvalidReportFormat() {
        boolean thrown = false;
        try {
            RunConfig.parse(new String[] {"--report=json"});
        } catch (IllegalArgumentException e) {
            thrown = true;
            expect(e.getMessage()).contains("Expected --report=");
        }
        expect(thrown).isTrue();
    }

    @Fact
    void throwsOnUnsupportedReportType() {
        boolean thrown = false;
        try {
            RunConfig.parse(new String[] {"--report=xml:build/report.xml"});
        } catch (IllegalArgumentException e) {
            thrown = true;
            expect(e.getMessage()).contains("Unsupported report type: xml");
        }
        expect(thrown).isTrue();
    }

    @Fact
    void parsesSeed() {
        var config = RunConfig.parse(new String[] {"--seed=42"});
        expect(config.seed()).isEqualTo(42L);
    }

    @Fact
    void parsesParallelism() {
        var config = RunConfig.parse(new String[] {"--parallelism=16"});
        expect(config.parallelism()).isEqualTo(16);
    }

    @Fact
    void parsesSequential() {
        var config = RunConfig.parse(new String[] {"--sequential"});
        expect(config.sequential()).isTrue();
        expect(config.parallelism()).isEqualTo(1);
    }

    @Fact
    void parsesExecutionModes() {
        var configSeq = RunConfig.parse(new String[] {"--execution=sequential"});
        expect(configSeq.sequential()).isTrue();
        expect(configSeq.parallelism()).isEqualTo(1);

        var configConc = RunConfig.parse(new String[] {"--execution=concurrent"});
        expect(configConc.sequential()).isFalse();
    }

    @Fact
    void throwsOnInvalidExecutionMode() {
        boolean thrown = false;
        try {
            RunConfig.parse(new String[] {"--execution=magic"});
        } catch (IllegalArgumentException e) {
            thrown = true;
            expect(e.getMessage()).contains("Unsupported execution mode: magic");
        }
        expect(thrown).isTrue();
    }

    @Fact
    void parsesFeatureFilters() {
        var config = RunConfig.parse(new String[] {"--feature=identity.registration", "--feature=billing.*"});
        expect(config.featureFilters().size()).isEqualTo(2);
        expect(config.featureFilters().get(0)).isEqualTo("identity.registration");
        expect(config.featureFilters().get(1)).isEqualTo("billing.*");
    }

    @Fact
    void parsesTagFilters() {
        var config = RunConfig.parse(new String[] {"--tag=smoke", "--tag=slow"});
        expect(config.tagFilters().size()).isEqualTo(2);
        expect(config.tagFilters().get(0)).isEqualTo("smoke");
        expect(config.tagFilters().get(1)).isEqualTo("slow");
    }

    @Fact
    void parsesOwnerFilters() {
        var config = RunConfig.parse(new String[] {"--owner=identity-team"});
        expect(config.ownerFilters().size()).isEqualTo(1);
        expect(config.ownerFilters().get(0)).isEqualTo("identity-team");
    }

    @Fact
    void parsesAreaFilters() {
        var config = RunConfig.parse(new String[] {"--area=Identity"});
        expect(config.areaFilters().size()).isEqualTo(1);
        expect(config.areaFilters().get(0)).isEqualTo("Identity");
    }

    @Fact
    void parsesRequirementFilters() {
        var config = RunConfig.parse(new String[] {"--requirement=REQ-123"});
        expect(config.requirementFilters().size()).isEqualTo(1);
        expect(config.requirementFilters().get(0)).isEqualTo("REQ-123");
    }

    @Fact
    void parsesHelp() {
        expect(RunConfig.parse(new String[] {"--help"}).help()).isTrue();
        expect(RunConfig.parse(new String[] {"-h"}).help()).isTrue();
    }
}
