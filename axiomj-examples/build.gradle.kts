plugins {
    id("su.kidoz.axiomj")
}

dependencies {
    testImplementation(project(":axiomj-api"))
    testImplementation(project(":axiomj-assertions"))
    testImplementation(project(":axiomj-di"))
    testImplementation(project(":axiomj-mock-core"))
    testImplementation(project(":axiomj-mock-bytecode"))
    testImplementation(project(":axiomj-property"))
    testImplementation(project(":axiomj-engine"))
}

val axiomjExampleClasses =
    listOf(
        "--scan-classpath",
        "--include-package=examples",
    )

axiomj {
    parallelism.set(4)
}

tasks.named("axiomjTest", su.kidoz.axiomj.plugin.AxiomJTestTask::class.java) {
    testClasses.set(axiomjExampleClasses)
}

// Run the bundled example suite as part of the build gate (`check`/`build`/CI).
tasks.named("check") {
    dependsOn("axiomjTest")
}

// keep runExamples for backward compatibility in the scripts for now
tasks.register<JavaExec>("runExamples") {
    group = "verification"
    description = "Runs the bundled AxiomJ example tests with JSON, Markdown, and Allure result output."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("su.kidoz.axiomj.engine.Main")
    args(
        "--parallelism=4",
        "--json=${layout.buildDirectory.file("reports/axiomj/report.json").get().asFile}",
        "--markdown=${layout.buildDirectory.file("reports/axiomj/report.md").get().asFile}",
        "--junit-xml=${layout.buildDirectory.file("reports/axiomj/TEST-axiomj.xml").get().asFile}",
        "--sarif=${layout.buildDirectory.file("reports/axiomj/axiomj.sarif").get().asFile}",
        "--html=${layout.buildDirectory.file("reports/axiomj/axiomj.html").get().asFile}",
        "--allure-results=${layout.buildDirectory.dir("allure-results").get().asFile}",
        *axiomjExampleClasses.toTypedArray(),
    )
}
