# AxiomJ

[![Java](https://img.shields.io/badge/Java-25%2B-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![Build](https://img.shields.io/badge/build-Gradle-02303A.svg?logo=gradle)](https://gradle.org/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

AxiomJ is a modern, **Java 25+**, MIT-licensed test framework that brings facts and
property-based tests, dependency injection, in-box mocking, fluent assertions,
feature-oriented grouping, concurrent execution, and agent-friendly reports together in one
coherent toolkit under the package root `su.kidoz.axiomj`.

## Highlights

- **Facts, properties, and stateful testing** — example-based `@Fact` tests, generative
  `@Property` tests, and model-based `StateMachine` testing with shrinking and reproducible
  seeds. Built-in generators cover primitives, strings, enums, records, and collections;
  custom `Arbitrary<T>` generators (with their own shrinking) plug in via `@ForAll(gen = …)`,
  and `.axiomj-corpus` replays failing seeds.
- **Fluent assertions** — `Expect.expect(...)` for scalars, strings, collections, maps and
  optionals, soft (aggregated) assertions, deep structural equivalence, and contextual
  `as(...)` messages.
- **Dependency injection & lifecycle** — constructor/field/parameter injection, test modules,
  `PER_TEST` / `SHARED` / `TRANSIENT` lifetimes with automatic `AutoCloseable` async cleanup,
  global `@BeforeSuite` / `@AfterSuite` hooks, `@AutoMock` for unbound interfaces, config
  coercion to Java Records, conditionally loaded `@Profile` modules, and `@Inject TestClock`
  and `@Inject TestLogger` support.
- **In-box mocking** — interface mocks with argument matchers, captors, stubbing, spies,
  strict-stub detection, in-order and timed verification; optional bytecode module for
  concrete-class mocks (instantiated constructor-free via Objenesis) and static method
  instrumentation via a Byte Buddy Java Agent.
- **Feature grouping** — first-class `@ProductArea`, `@Feature`, `@Scenario`, `@Requirement`,
  and `@Owner` metadata, independent of package structure.
- **Concurrency and sequencing** — independent tests run concurrently on virtual threads;
  `@DependsOn` (forward) and `@DependBy` (reverse) express ordering and gating, while
  `@ResourceLock` prevents collisions on shared state. Built-in `@Retry` handles flaky tests.
- **Agent-friendly reports** — console, JSON, HTML, SARIF, Markdown, and JUnit XML.
- **Robust execution** — classpath scanning, package filters, and `--fail-fast` support.

## Requirements

- Java 25+ (uses Scoped Values, unnamed variables, and pattern switches)
- Gradle (the wrapper is included; no local install required)

## Quick start

```bash
git clone <your-fork-url> axiomj
cd axiomj

./gradlew build                       # compile, format-check, and assemble all modules
./gradlew :axiomj-examples:axiomjTest # run the bundled example tests
```

With [`just`](https://github.com/casey/just):

```bash
just build      # ./gradlew build
just test       # run the example tests
just fmt        # auto-format (Spotless)
just fmt-check  # verify formatting
```

## Writing tests

### Facts and assertions

```java
import su.kidoz.axiomj.api.Fact;
import static su.kidoz.axiomj.assertions.Expect.expect;

final class CalculatorTest {

    @Fact
    void addsTwoNumbers() {
        expect(2 + 2).isEqualTo(4);
    }

    @Fact
    void collectionsAndSoftAssertions() {
        var users = java.util.List.of("ada", "bob");
        expect(users).hasSize(2).contains("ada").doesNotContain("zoe");

        Expect.softly(() -> {            // all failures reported together
            expect(users).isNotEmpty();
            expect(users.getFirst()).isEqualToIgnoringCase("ADA");
        });
    }
}
```

### Property-based tests

```java
import su.kidoz.axiomj.api.*;
import static su.kidoz.axiomj.assertions.Expect.expect;

final class MathProperties {

    @Property(tries = 200)
    void additionIsCommutative(
            @ForAll @IntRange(min = -10_000, max = 10_000) int a,
            @ForAll @IntRange(min = -10_000, max = 10_000) int b) {
        expect(a + b).isEqualTo(b + a);
    }
}
```

For types the built-in generators don't cover, supply a custom `Arbitrary` via
`@ForAll(gen = …)` — its `shrink` is used to minimize failing samples, and the same mechanism
drives model-based testing (inject an `Arbitrary<List<Action<M,S>>>` and run a `StateMachine`):

```java
public final class Emails implements Arbitrary<String> {
    public String generate(GenerationContext ctx) { return "user" + ctx.random().nextInt(1000) + "@example.com"; }
}

@Property
void parsesAnyEmail(@ForAll(gen = Emails.class) String email) {
    expect(EmailParser.parse(email)).isPresent();
}
```

### Dependency injection and mocking

```java
import su.kidoz.axiomj.api.*;
import su.kidoz.axiomj.mock.Arg;
import su.kidoz.axiomj.mock.Mocks;
import static su.kidoz.axiomj.assertions.Expect.expect;

@AutoMock                                  // unbound interfaces become mocks automatically
final class GreetingServiceTest {

    @Inject GreetingService service;       // its Repository dependency is auto-mocked
    @Mock Repository repository;           // the same mock the service received

    @Fact
    void greetsKnownUser() {
        Mocks.when(() -> repository.find("42")).thenReturn(java.util.Optional.of("Ada"));

        expect(service.greet("42")).isEqualTo("Hello, Ada");
        Mocks.verify(() -> repository.find(Arg.any())).calledOnce();
    }
}
```

Concrete classes can be mocked through the optional bytecode module:

```java
import su.kidoz.axiomj.mock.bytecode.BytecodeMocks;

var calculator = BytecodeMocks.mockClass(Calculator.class);
Mocks.when(() -> calculator.add(Arg.anyInt(), Arg.anyInt())).thenReturn(42);
```

### Feature grouping and sequencing

```java
import su.kidoz.axiomj.api.*;
import static su.kidoz.axiomj.assertions.Expect.expect;

@ProductArea("Identity")
@Feature(id = "identity.registration", name = "User registration workflow", owner = "identity-team")
final class RegistrationFeatureTest {

    @Fact @Order(1) @Scenario("new account is created") @Requirement("REQ-ID-100")
    void createAccount() {
        expect(true).isTrue();
    }

    @Fact(dependsOn = "createAccount") @Order(2) @Scenario("welcome email waits for the account")
    void sendWelcomeEmail() {
        expect(true).isTrue();
    }
}
```

`@DependsOn("a")` (or `@Fact(dependsOn = "a")`) means “this test depends on `a`”; `@DependBy("b")`
is the inverse: “`b` depends on this test”. Independent tests in the same layer run
concurrently; a test runs only after its prerequisites pass, and dependents are skipped if a
prerequisite fails or is skipped. Use `@Execution(ExecutionMode.SEQUENTIAL)` to force a class
one-at-a-time.

## Modules

| Module | Responsibility |
|---|---|
| `axiomj-api` | Public annotations, DI contracts, `TestContext` |
| `axiomj-assertions` | Fluent `Expect` assertions |
| `axiomj-di` | Test dependency-injection container |
| `axiomj-property` | Property generators and shrinking |
| `axiomj-mock-core` | Interface mocks, matchers, captors, verification |
| `axiomj-mock-bytecode` | Optional concrete-class mocking (Byte Buddy) |
| `axiomj-engine` | Discovery, execution, lifecycle, reporting, CLI |
| `axiomj-gradle-plugin` | Gradle integration (`axiomjTest` task) |
| `axiomj-examples` | Bundled, runnable example tests |

The core stays MIT-licensed and dependency-free; the only third-party dependency is Byte
Buddy, isolated in the optional `axiomj-mock-bytecode` module.

## Reports

The runner writes plain console output plus optional machine-readable reports:

```text
--json=build/report.json
--markdown=build/report.md
--junit-xml=build/TEST-axiomj.xml
--sarif=build/axiomj.sarif
--html=build/axiomj.html
--allure-results=build/allure-results
```

The Markdown report is designed to be read by humans and coding agents: it includes the
summary, per-test status, feature and scenario metadata, source-file path, dependency chain,
property seeds/samples, and failure details. The `runExamples` Gradle task produces all
report formats under `build/`.

## Development

- Formatting is enforced by **Spotless** (palantir-java-format, 120 columns). `spotlessCheck`
  is wired into `check`, so `./gradlew build` fails on unformatted code — run `just fmt`
  before committing.
- The test suites are wired into `check`: `./gradlew build` runs the engine self-tests
  (`:axiomj-engine:runEngineTests`) and the example suite (`:axiomj-examples:axiomjTest`), so
  a test failure fails the build.
- CI (`.github/workflows/ci.yml`) verifies formatting, builds, and runs the suites on every
  push and PR.
- Durable design notes and decision records live under `.agents/contexts/`.

## Status

AxiomJ is at version `0.1.0` and is not yet published to a package repository; use it
by cloning this project. The API is still evolving.

## License

MIT — see [`LICENSE`](LICENSE).

## Author

Aleksandr Pavlov <ckidoz@gmail.com>
