import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

dependencies {
    api(project(":axiomj-api"))
    api(project(":axiomj-di"))
    api(project(":axiomj-mock-core"))
    api(project(":axiomj-property"))
    api(project(":axiomj-assertions"))
}

val runEngineTests =
    tasks.register<JavaExec>("runEngineTests") {
        group = "verification"
        description = "Runs the internal engine tests."
        classpath = sourceSets["main"].runtimeClasspath + sourceSets["test"].runtimeClasspath
        mainClass.set("su.kidoz.axiomj.engine.Main")
        args(
            "su.kidoz.axiomj.engine.RunConfigTest",
            "su.kidoz.axiomj.engine.DependencyGraphTest",
            "su.kidoz.axiomj.engine.JsonSupportTest",
            "su.kidoz.axiomj.engine.ContainerTest",
            "su.kidoz.axiomj.engine.ShrinkerTest",
            "su.kidoz.axiomj.engine.JunitXmlReportTest",
            "su.kidoz.axiomj.engine.LifecycleSafetyTest",
            "su.kidoz.axiomj.engine.FailureCorpusTest",
            "su.kidoz.axiomj.engine.MarkdownReportTest",
        )
    }

// Make the engine's own tests part of the build gate (`check`/`build`/CI), not just a manual task.
tasks.named("check") {
    dependsOn(runEngineTests)
}

// JaCoCo coverage for the engine self-tests. AxiomJ's runner forks a JVM and calls System.exit,
// so the agent is attached to that fork (applyTo) and dumps on JVM shutdown.
the<JacocoPluginExtension>().applyTo(runEngineTests.get())

tasks.register<JacocoReport>("jacocoEngineReport") {
    group = "verification"
    description = "Generates a JaCoCo coverage report from the engine self-tests."
    dependsOn(runEngineTests)
    executionData(runEngineTests.get())
    sourceSets(sourceSets["main"])
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
