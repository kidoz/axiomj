# AxiomJ Justfile

# Default task when running `just` without arguments
default:
    @just --list

# Build the entire project
build:
    ./gradlew build

# Compile the project
compile:
    ./gradlew compileJava compileTestJava

# Run the tests
test:
    ./gradlew :axiomj-examples:axiomjTest

# Run the bundled examples and write JSON/Markdown/Allure reports
run-examples:
    ./gradlew :axiomj-examples:runExamples

# Auto-format all Java and Gradle sources
fmt:
    ./gradlew spotlessApply

# Verify formatting without modifying files
fmt-check:
    ./gradlew spotlessCheck

# Clean the build directories
clean:
    ./gradlew clean

# Run engine tests
test-engine:
    ./gradlew :axiomj-engine:runEngineTests
