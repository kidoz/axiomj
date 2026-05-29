plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("axiomjPlugin") {
            id = "su.kidoz.axiomj"
            implementationClass = "su.kidoz.axiomj.plugin.AxiomJPlugin"
        }
    }
}

dependencies {
    // we do not strictly need a dependency on the engine to execute it in another JVM via JavaExec or similar,
    // but the plugin needs to configure it.
}
