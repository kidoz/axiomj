import org.gradle.testing.jacoco.tasks.JacocoReport

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

// ---- Whole-framework JaCoCo coverage from the example run --------------------------------------
// The example suite exercises every framework module. AxiomJ runs it in a forked JVM via the plugin
// task, so the JaCoCo agent is passed through the task's jvmArgs (the agent dumps on JVM exit), and
// a single report aggregates the coverage against every module's main sources.

val coverageExec = layout.buildDirectory.file("jacoco/axiomjTest.exec")
val jacocoAgentJar = layout.buildDirectory.file("tmp/jacoco/jacocoagent.jar")

val extractJacocoAgent =
    tasks.register<Copy>("extractJacocoAgent") {
        from({ zipTree(configurations["jacocoAgent"].singleFile) })
        include("jacocoagent.jar")
        into(layout.buildDirectory.dir("tmp/jacoco"))
    }

tasks.named("axiomjTest", su.kidoz.axiomj.plugin.AxiomJTestTask::class.java) {
    dependsOn(extractJacocoAgent)
    // JaCoCo cannot coexist with class-redefinition mocking (static/constructor): if it has already
    // instrumented a class, Byte Buddy's redefinition no longer installs the mock. The redefined
    // targets are all test fixtures in `examples.*`, which the report does not include anyway, so we
    // exclude that package from instrumentation — framework classes (su.kidoz.axiomj.*) are still covered.
    jvmArgs.add(
        provider {
            val agent = jacocoAgentJar.get().asFile.absolutePath
            val dest = coverageExec.get().asFile.absolutePath
            "-javaagent:$agent=destfile=$dest,dumponexit=true,excludes=examples.*"
        },
    )
}

val frameworkModules =
    listOf(
        "axiomj-api",
        "axiomj-assertions",
        "axiomj-di",
        "axiomj-mock-core",
        "axiomj-mock-bytecode",
        "axiomj-property",
        "axiomj-engine",
    )
frameworkModules.forEach { evaluationDependsOn(":$it") }

fun JacocoReport.aggregateFrameworkSources() {
    frameworkModules.forEach { name ->
        val main = project(":$name").extensions.getByType(SourceSetContainer::class.java).getByName("main")
        sourceDirectories.from(main.allJava.srcDirs)
        classDirectories.from(main.output.classesDirs)
    }
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register<JacocoReport>("jacocoExamplesReport") {
    group = "verification"
    description = "Aggregated JaCoCo coverage across all framework modules, from the example test run."
    dependsOn("axiomjTest")
    executionData(coverageExec)
    aggregateFrameworkSources()
}

// A single number for the whole project: merges the example run with the engine self-tests, since those
// two suites write separate .exec files and neither alone reflects total coverage.
tasks.register<JacocoReport>("jacocoMergedReport") {
    group = "verification"
    description = "Merged JaCoCo coverage (example run + engine self-tests) across all framework modules."
    dependsOn("axiomjTest", ":axiomj-engine:runEngineTests")
    executionData(
        coverageExec,
        project(":axiomj-engine").layout.buildDirectory.file("jacoco/runEngineTests.exec"),
    )
    aggregateFrameworkSources()
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
