pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "axiomj"

include("axiomj-api")
include("axiomj-assertions")
include("axiomj-di")
include("axiomj-mock-core")
include("axiomj-mock-bytecode")
include("axiomj-property")
include("axiomj-engine")
include("axiomj-examples")
includeBuild("axiomj-gradle-plugin")
