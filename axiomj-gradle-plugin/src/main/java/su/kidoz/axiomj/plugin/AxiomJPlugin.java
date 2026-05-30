package su.kidoz.axiomj.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;

import java.util.List;

public class AxiomJPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        var axiomjExt = project.getExtensions().create("axiomj", AxiomJExtension.class);

        project.getTasks().register("axiomjTest", AxiomJTestTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Runs AxiomJ tests.");
            
            project.getPlugins().withId("java", plugin -> {
                var java = project.getExtensions().getByType(JavaPluginExtension.class);
                var sourceSets = java.getSourceSets();
                SourceSet testSourceSet = sourceSets.getByName("test");
                
                task.getClasspath().from(testSourceSet.getRuntimeClasspath());
            });

            var buildDir = project.getLayout().getBuildDirectory();
            // Default to scanning the test classpath so a freshly-applied plugin discovers tests instead of
            // running nothing. The engine fails (non-zero exit) if this selects no tests, so `axiomjTest`
            // can never report a green build while executing zero tests.
            task.getTestClasses().convention(List.of("--scan-classpath"));
            task.getParallelism().convention(axiomjExt.getParallelism());
            task.getFailFast().convention(axiomjExt.getFailFast());
            task.getSeed().convention(axiomjExt.getSeed());
            task.getJsonReport().convention(buildDir.file("reports/axiomj/report.json"));
            task.getMarkdownReport().convention(buildDir.file("reports/axiomj/report.md"));
            task.getJunitXmlReport().convention(buildDir.file("reports/axiomj/TEST-axiomj.xml"));
            task.getSarifReport().convention(buildDir.file("reports/axiomj/axiomj.sarif"));
            task.getHtmlReport().convention(buildDir.file("reports/axiomj/axiomj.html"));
            task.getAllureResultsDir().convention(buildDir.dir("allure-results"));
        });
    }
}
