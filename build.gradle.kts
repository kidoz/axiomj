import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

plugins {
    `java-library`
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone) apply false
}

spotless {
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "net.ltgt.errorprone")
    apply(plugin = "jacoco")

    group = "su.kidoz.axiomj"
    version = "0.1.0"

    // AxiomJ runs its tests in a forked JVM via JavaExec, so JaCoCo attaches as a Java agent to
    // that fork (see each module's coverage task). Pin a JaCoCo that understands Java 25 bytecode.
    configure<JacocoPluginExtension> {
        toolVersion = "0.8.13"
    }

    dependencies {
        "errorprone"(rootProject.libs.errorprone.core)
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    tasks.test {
        enabled = false
    }

    configure<SpotlessExtension> {
        java {
            target("src/**/*.java")
            palantirJavaFormat(libs.versions.palantir.get()).formatJavadoc(true)
            removeUnusedImports()
            importOrder()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
