package su.kidoz.axiomj.plugin;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.DefaultTask;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.util.ArrayList;

public abstract class AxiomJTestTask extends DefaultTask {

    @InputFiles
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<Integer> getParallelism();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getJsonReport();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getMarkdownReport();

    @OutputDirectory
    @Optional
    public abstract DirectoryProperty getAllureResultsDir();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getJunitXmlReport();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getSarifReport();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getHtmlReport();

    @Input
    @Optional
    public abstract Property<Boolean> getFailFast();

    @Input
    @Optional
    public abstract Property<Long> getSeed();

    @Input
    @Optional
    public abstract ListProperty<String> getTestClasses();

    /** Extra raw CLI arguments passed through verbatim (e.g. {@code --feature=...}, {@code --tag=...}). */
    @Input
    @Optional
    public abstract ListProperty<String> getExtraArgs();

    /** Extra JVM arguments (e.g. {@code -javaagent:...}, memory flags) passed to the test execution process. */
    @Input
    @Optional
    public abstract ListProperty<String> getJvmArgs();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void runTests() {
        getExecOperations().javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set("su.kidoz.axiomj.engine.Main");

            if (getJvmArgs().isPresent()) {
                spec.jvmArgs(getJvmArgs().get());
            }

            var argsList = new ArrayList<String>();
            if (getParallelism().isPresent()) {
                argsList.add("--parallelism=" + getParallelism().get());
            }
            if (getJsonReport().isPresent()) {
                argsList.add("--json=" + getJsonReport().get().getAsFile().getAbsolutePath());
            }
            if (getMarkdownReport().isPresent()) {
                argsList.add("--markdown=" + getMarkdownReport().get().getAsFile().getAbsolutePath());
            }
            if (getAllureResultsDir().isPresent()) {
                argsList.add("--allure-results=" + getAllureResultsDir().get().getAsFile().getAbsolutePath());
            }
            if (getJunitXmlReport().isPresent()) {
                argsList.add("--junit-xml=" + getJunitXmlReport().get().getAsFile().getAbsolutePath());
            }
            if (getSarifReport().isPresent()) {
                argsList.add("--sarif=" + getSarifReport().get().getAsFile().getAbsolutePath());
            }
            if (getHtmlReport().isPresent()) {
                argsList.add("--html=" + getHtmlReport().get().getAsFile().getAbsolutePath());
            }
            if (getFailFast().getOrElse(false)) {
                argsList.add("--fail-fast");
            }
            if (getSeed().isPresent()) {
                argsList.add("--seed=" + getSeed().get());
            }
            if (getExtraArgs().isPresent()) {
                argsList.addAll(getExtraArgs().get());
            }
            if (getTestClasses().isPresent()) {
                argsList.addAll(getTestClasses().get());
            }

            spec.args(argsList);
        });
    }
}
