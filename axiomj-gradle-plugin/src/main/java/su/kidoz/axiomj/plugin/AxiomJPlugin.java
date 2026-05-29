package su.kidoz.axiomj.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;

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

            task.getParallelism().convention(axiomjExt.getParallelism());
            task.getJsonReport().convention(project.getLayout().getBuildDirectory().file("reports/axiomj/report.json"));
            task.getMarkdownReport().convention(project.getLayout().getBuildDirectory().file("reports/axiomj/report.md"));
            task.getAllureResultsDir().convention(project.getLayout().getBuildDirectory().dir("allure-results"));
        });
    }
}
