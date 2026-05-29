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

    @Input
    @Optional
    public abstract ListProperty<String> getTestClasses();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void runTests() {
        getExecOperations().javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set("su.kidoz.axiomj.engine.Main");

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
            if (getTestClasses().isPresent()) {
                argsList.addAll(getTestClasses().get());
            }

            spec.args(argsList);
        });
    }
}
