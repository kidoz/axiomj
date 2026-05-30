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
        )
    }

// Make the engine's own tests part of the build gate (`check`/`build`/CI), not just a manual task.
tasks.named("check") {
    dependsOn(runEngineTests)
}
