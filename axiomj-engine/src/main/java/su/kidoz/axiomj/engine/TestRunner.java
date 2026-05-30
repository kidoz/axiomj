package su.kidoz.axiomj.engine;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import su.kidoz.axiomj.api.*;
import su.kidoz.axiomj.di.Config;
import su.kidoz.axiomj.di.SimpleContainer;
import su.kidoz.axiomj.di.TestModule;
import su.kidoz.axiomj.mock.Mocks;
import su.kidoz.axiomj.property.BuiltInGenerators;
import su.kidoz.axiomj.property.GenerationContext;
import su.kidoz.axiomj.property.Shrinker;

public final class TestRunner {
    // Upper bound on shrink trials per failing property, to keep shrinking (each trial re-runs the full
    // per-test lifecycle) cheap and bounded. Shrinking is best-effort, so capping only affects how minimal
    // the reported sample is, never correctness.
    private static final int MAX_SHRINK_TRIALS = 200;

    // Grace period to let an interrupted/cancelled test thread unwind (release shared services, finish
    // cleanup) before the runner proceeds to class teardown. Cooperative tests stop well within this;
    // non-cooperative ones cannot be force-killed on the JVM, so this only bounds the race window.
    private static final long ABORT_DRAIN_MILLIS = 500;

    // Cache of instantiated @ForAll(gen = ...) custom generators, keyed by generator class.
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, su.kidoz.axiomj.property.Arbitrary<?>>
            ARBITRARY_GENERATORS = new java.util.concurrent.ConcurrentHashMap<>();

    private final PrintStream out;

    // Named resource locks coordinating @ResourceLock tests within this run; instance-scoped so the
    // map's lifetime is bounded by the run rather than leaking for the life of the JVM.
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.locks.ReentrantLock>
            resourceLocks = new java.util.concurrent.ConcurrentHashMap<>();

    // Per-class root container, set while a class runs (classes are processed sequentially in run()).
    private volatile SimpleContainer classRoot;

    public TestRunner(PrintStream out) {
        this.out = out;
    }

    public RunSummary run(RunConfig config) throws IOException {
        var started = Instant.now();
        var results = new ArrayList<TestResult>();
        var threadFactory = Thread.ofVirtual().name("axiomj-test-", 0).factory();
        var corpus = new FailureCorpus();

        List<Class<?>> parsedClasses = new ArrayList<>();
        List<Method> allBeforeSuite = new ArrayList<>();
        List<Method> allAfterSuite = new ArrayList<>();

        for (String className : config.classNames()) {
            try {
                Class<?> testClass = Class.forName(className);
                parsedClasses.add(testClass);
                var plan = TestPlan.of(testClass);
                allBeforeSuite.addAll(plan.beforeSuite());
                allAfterSuite.addAll(plan.afterSuite());
            } catch (Throwable error) {
                var result = classFailureResult(className, unwrap(error));
                results.add(result);
                print(result);
            }
        }

        // A @BeforeSuite failure is recorded as a failed result and the test classes are skipped, rather than
        // calling System.exit — run(...) is a library API and only Main decides the process exit code.
        boolean suiteSetupOk = true;
        try {
            invokeAllStatic(allBeforeSuite);
        } catch (Throwable error) {
            var result = suiteFailureResult("@BeforeSuite", unwrap(error));
            results.add(result);
            print(result);
            suiteSetupOk = false;
        }

        if (suiteSetupOk) {
            try (ExecutorService executor = Executors.newFixedThreadPool(config.parallelism(), threadFactory)) {
                for (Class<?> testClass : parsedClasses) {
                    if (config.failFast() && results.stream().anyMatch(TestResult::failed)) {
                        break;
                    }
                    try {
                        var plan = TestPlan.of(testClass);
                        var root = rootContainerFor(testClass, config.activeProfiles());
                        classRoot = root;
                        boolean beforeAllDone = false;
                        try {
                            invokeAllStatic(plan.beforeAll());
                            beforeAllDone = true;
                            var classResults = runClass(config, executor, testClass, plan, corpus);
                            results.addAll(classResults);
                        } finally {
                            try {
                                if (beforeAllDone) {
                                    invokeAllStatic(plan.afterAll());
                                }
                            } finally {
                                classRoot = null;
                                root.close();
                            }
                        }
                    } catch (Throwable error) {
                        var result = classFailureResult(testClass.getName(), unwrap(error));
                        results.add(result);
                        print(result);
                    }
                }
            }

            // A @AfterSuite failure is a real failure (it can leave global state dirty), so it becomes a failed
            // result reflected in the summary rather than a silent warning that lets the run pass.
            try {
                invokeAllStatic(allAfterSuite);
            } catch (Throwable error) {
                var result = suiteFailureResult("@AfterSuite", unwrap(error));
                results.add(result);
                print(result);
            }
        }

        corpus.save();

        long duration = Duration.between(started, Instant.now()).toMillis();
        int failed = (int) results.stream().filter(TestResult::failed).count();
        int skipped = (int) results.stream().filter(TestResult::skipped).count();
        int passed = (int) results.stream().filter(TestResult::passed).count();
        var summary = new RunSummary(results.size(), passed, failed, skipped, duration);
        out.printf(
                "%nAxiomJ: %d tests, %d passed, %d failed, %d skipped, %d ms%n",
                summary.total(), summary.passed(), summary.failed(), summary.skipped(), summary.durationMillis());

        if (config.jsonReport() != null) {
            write(config.jsonReport(), JsonReport.render(results, summary));
        }
        if (config.markdownReport() != null) {
            write(config.markdownReport(), MarkdownReport.render(results, summary, config));
        }
        if (config.junitXmlReport() != null) {
            write(config.junitXmlReport(), JunitXmlReport.render(results, summary));
        }
        if (config.sarifReport() != null) {
            write(config.sarifReport(), SarifReport.render(results, config));
        }
        if (config.htmlReport() != null) {
            write(config.htmlReport(), HtmlReport.render(results, summary, config));
        }
        if (config.allureResultsDir() != null) {
            AllureReport.write(config.allureResultsDir(), results);
        }
        return summary;
    }

    private List<TestResult> runClass(
            RunConfig config, ExecutorService executor, Class<?> testClass, TestPlan plan, FailureCorpus corpus) {
        var output = new ArrayList<TestResult>();
        var nodes = new LinkedHashMap<String, TestNode>();
        var duplicateNames = new LinkedHashSet<String>();
        for (Method method : plan.tests()) {
            if (!matchesFilters(config, testClass, method)) {
                continue;
            }
            var previous = nodes.put(
                    method.getName(), new TestNode(method, new ArrayList<>(dependsOn(method)), order(method)));
            if (previous != null) {
                duplicateNames.add(method.getName());
            }
        }

        for (Method method : plan.tests()) {
            if (!nodes.containsKey(method.getName())) {
                continue;
            }
            var dependBy = method.getAnnotation(DependBy.class);
            if (dependBy != null) {
                for (var target : dependBy.value()) {
                    var targetNode = nodes.get(target);
                    if (targetNode != null) {
                        targetNode.dependsOn().add(method.getName());
                    }
                }
            }
        }

        if (!duplicateNames.isEmpty()) {
            for (var node : nodes.values()) {
                if (duplicateNames.contains(node.method().getName())) {
                    var result = failedResult(
                            testClass,
                            node.method(),
                            now(),
                            0,
                            new IllegalStateException("Overloaded test method name is ambiguous for dependencies: "
                                    + node.method().getName()),
                            Map.of("dependencyError", true));
                    output.add(result);
                    print(result);
                }
            }
            return output;
        }

        boolean classSequential = (executionMode(testClass) == ExecutionMode.SAME_THREAD
                || executionMode(testClass) == ExecutionMode.SEQUENTIAL
                || config.parallelism() == 1);
        var remaining = new LinkedHashSet<>(nodes.keySet());
        var done = new LinkedHashMap<String, TestResult>();

        while (!remaining.isEmpty()) {
            boolean changed = false;

            for (var name : List.copyOf(remaining)) {
                var node = nodes.get(name);
                var missing = firstMissingDependency(node, nodes);
                if (missing != null) {
                    var result = failedResult(
                            testClass,
                            node.method(),
                            now(),
                            0,
                            new IllegalStateException("Unknown dependency '" + missing + "' for test "
                                    + node.method().getName()),
                            Map.of("dependencyError", true, "missingDependency", missing));
                    output.add(result);
                    done.put(name, result);
                    remaining.remove(name);
                    print(result);
                    changed = true;
                }
            }

            for (var name : List.copyOf(remaining)) {
                var node = nodes.get(name);
                var failedDependency = firstFinishedNonPassingDependency(node, done);
                if (failedDependency != null) {
                    var dependencyResult = done.get(failedDependency);
                    var result = skippedResult(
                            testClass,
                            node.method(),
                            "Skipped because dependency '" + failedDependency + "' was " + dependencyResult.status());
                    output.add(result);
                    done.put(name, result);
                    remaining.remove(name);
                    print(result);
                    changed = true;
                }
            }

            var ready = remaining.stream()
                    .map(nodes::get)
                    .filter(node -> dependenciesPassed(node, done))
                    .sorted(Comparator.comparingInt(TestNode::order)
                            .thenComparing(node -> node.method().getName()))
                    .toList();

            if (ready.isEmpty()) {
                if (!changed) {
                    for (var name : List.copyOf(remaining)) {
                        var node = nodes.get(name);
                        var result = failedResult(
                                testClass,
                                node.method(),
                                now(),
                                0,
                                new IllegalStateException(
                                        "Dependency cycle or unresolved sequence involving: " + remaining),
                                Map.of("dependencyError", true, "remaining", remaining.toString()));
                        output.add(result);
                        done.put(name, result);
                        remaining.remove(name);
                        print(result);
                    }
                }
                continue;
            }

            var layer = ready;
            var layerResults = classSequential
                            || layer.stream()
                                    .anyMatch(node -> (executionMode(node.method()) == ExecutionMode.SAME_THREAD
                                            || executionMode(node.method()) == ExecutionMode.SEQUENTIAL))
                    ? runSequential(config, testClass, plan, layer, corpus)
                    : runConcurrent(config, executor, testClass, plan, layer, corpus);
            for (var result : layerResults) {
                output.add(result);
                done.put(result.methodName(), result);
                remaining.remove(result.methodName());
                print(result);
                if (config.failFast() && result.failed()) {
                    return output;
                }
            }
            changed = true;
        }
        return output;
    }

    private List<TestResult> runSequential(
            RunConfig config, Class<?> testClass, TestPlan plan, List<TestNode> nodes, FailureCorpus corpus) {
        var results = new ArrayList<TestResult>();
        for (var node : nodes) {
            var result = runMethod(testClass, plan, node.method(), config.seed(), corpus);
            results.add(result);
            if (config.failFast() && result.failed()) {
                break;
            }
        }
        return results;
    }

    private List<TestResult> runConcurrent(
            RunConfig config,
            ExecutorService executor,
            Class<?> testClass,
            TestPlan plan,
            List<TestNode> nodes,
            FailureCorpus corpus) {
        var results = new ArrayList<TestResult>();
        var futures = new LinkedHashMap<TestNode, Future<TestResult>>();
        for (var node : nodes) {
            futures.put(node, executor.submit(() -> runMethod(testClass, plan, node.method(), config.seed(), corpus)));
        }
        boolean aborted = false;
        for (var entry : futures.entrySet()) {
            if (aborted) {
                break;
            }
            var node = entry.getKey();
            var future = entry.getValue();
            try {
                var result = future.get();
                results.add(result);
                if (config.failFast() && result.failed()) {
                    cancelAndDrain(futures.values());
                    aborted = true;
                }
            } catch (Throwable error) {
                results.add(failedResult(testClass, node.method(), now(), 0, unwrap(error), Map.of()));
                if (config.failFast()) {
                    cancelAndDrain(futures.values());
                    aborted = true;
                }
            }
        }
        if (aborted) {
            // Tests cancelled by --fail-fast (no result collected) are reported as skipped rather than
            // vanishing silently from the report.
            var collected = new LinkedHashSet<String>();
            for (var result : results) {
                collected.add(result.methodName());
            }
            for (var node : nodes) {
                if (!collected.contains(node.method().getName())) {
                    results.add(skippedResult(testClass, node.method(), "Skipped: run aborted by --fail-fast"));
                }
            }
        }
        return results;
    }

    // Cancels every future, then waits a bounded time for them to settle, so in-flight tests stop
    // touching shared state before the class container is closed. Best-effort.
    private static void cancelAndDrain(java.util.Collection<Future<TestResult>> futures) {
        for (var future : futures) {
            future.cancel(true);
        }
        for (var future : futures) {
            try {
                future.get(ABORT_DRAIN_MILLIS, TimeUnit.MILLISECONDS);
            } catch (Throwable ignored) {
                // cancelled/failed/slow — best-effort drain, nothing to do
            }
        }
    }

    private static void joinQuietly(Thread thread, long millis) {
        try {
            thread.join(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private TestResult runMethod(Class<?> testClass, TestPlan plan, Method method, long runSeed, FailureCorpus corpus) {
        long timeout = timeoutMillis(method);
        if (timeout <= 0) {
            return executeMethod(testClass, plan, method, runSeed, corpus);
        }
        // Enforce the timeout from the moment the test actually starts, independent of whether
        // it runs sequentially or concurrently and independent of the order futures are consumed.
        long startedMillis = now();
        long startedNanos = System.nanoTime();
        var future = new java.util.concurrent.CompletableFuture<TestResult>();
        var worker = Thread.ofVirtual().name("axiomj-timeout-", 0).start(() -> {
            try {
                future.complete(executeMethod(testClass, plan, method, runSeed, corpus));
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            worker.interrupt();
            // Give the interrupted test a bounded chance to unwind before the class root is torn down,
            // so a cooperative test stops touching shared services before they are closed.
            joinQuietly(worker, ABORT_DRAIN_MILLIS);
            return failedResult(
                    testClass,
                    method,
                    startedMillis,
                    elapsedMillis(startedNanos),
                    new AssertionError("Timed out after " + timeout + " ms"),
                    Map.of("timeoutMillis", timeout));
        } catch (Throwable error) {
            return failedResult(
                    testClass,
                    method,
                    startedMillis,
                    elapsedMillis(startedNanos),
                    unwrap(error),
                    baseMetadata(runSeed));
        }
    }

    private TestResult executeMethod(
            Class<?> testClass, TestPlan plan, Method method, long runSeed, FailureCorpus corpus) {
        var retryAnnotation = method.getAnnotation(Retry.class);
        if (retryAnnotation == null) {
            retryAnnotation = testClass.getAnnotation(Retry.class);
        }
        int maxAttempts = retryAnnotation != null ? Math.max(1, retryAnnotation.maxAttempts()) : 1;

        var lockAnnotation = method.getAnnotation(ResourceLock.class);
        if (lockAnnotation == null) {
            lockAnnotation = testClass.getAnnotation(ResourceLock.class);
        }

        java.util.concurrent.locks.ReentrantLock lock = null;
        if (lockAnnotation != null) {
            lock = resourceLocks.computeIfAbsent(
                    lockAnnotation.value(), k -> new java.util.concurrent.locks.ReentrantLock());
        }

        TestResult lastResult = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (lock != null) {
                lock.lock();
            }
            try {
                if (method.isAnnotationPresent(Property.class)) {
                    lastResult = runProperty(testClass, plan, method, runSeed, corpus);
                } else {
                    lastResult = runFact(testClass, plan, method, runSeed);
                }

                if (lastResult.passed() || lastResult.skipped()) {
                    return lastResult; // Success or skipped, don't retry
                }
            } finally {
                if (lock != null) {
                    lock.unlock();
                }
            }
        }
        return lastResult; // All attempts failed
    }

    private TestResult runFact(Class<?> testClass, TestPlan plan, Method method, long runSeed) {
        long startedMillis = now();
        long startedNanos = System.nanoTime();
        var metadata = baseMetadata(runSeed);
        var logger = new DefaultTestLogger();
        return Mocks.inSession(() -> {
            SimpleContainer container = null;
            try {
                var invocation = newInvocation(testClass, method, runSeed, 0, false, logger);
                container = invocation.container();
                invokeEach(plan.beforeEach(), invocation.instance(), invocation.container(), invocation.context());
                invokeBodyThenAfterEach(
                        method,
                        invocation,
                        plan.afterEach(),
                        resolveParameters(method, invocation.container(), invocation.context(), null));
                Mocks.verifyStrictStubs();
                metadata.put("log", logger.getOutput());
                return passedResult(testClass, method, startedMillis, startedNanos, metadata);
            } catch (Throwable error) {
                metadata.put("log", logger.getOutput());
                return failedResult(
                        testClass, method, startedMillis, elapsedMillis(startedNanos), unwrap(error), metadata);
            } finally {
                if (container != null) {
                    container.close();
                }
            }
        });
    }

    private TestResult runProperty(
            Class<?> testClass, TestPlan plan, Method method, long runSeed, FailureCorpus corpus) {
        long startedMillis = now();
        long startedNanos = System.nanoTime();
        var property = method.getAnnotation(Property.class);
        long seed = property.seed() == Long.MIN_VALUE ? stableSeed(runSeed, testClass, method) : property.seed();
        var metadata = baseMetadata(seed);
        metadata.put("tries", property.tries());
        return Mocks.inSession(() -> {
            DefaultTestLogger lastLogger = new DefaultTestLogger();
            try {
                String methodId = id(testClass, method);
                for (long corpusSeed : corpus.getSeeds(methodId)) {
                    var random = new SplittableRandom(corpusSeed);
                    Object[] generated = generatedArguments(method, random, corpusSeed, -1);
                    var logger = new DefaultTestLogger();
                    try {
                        invokePropertyOnce(testClass, plan, method, corpusSeed, -1, generated, logger);
                        corpus.removePass(methodId, corpusSeed);
                        lastLogger = logger;
                    } catch (Throwable failure) {
                        Object[] minimized = shrink(testClass, plan, method, corpusSeed, -1, generated);
                        metadata.put("failingAttempt", -1);
                        metadata.put("sample", Arrays.deepToString(generated));
                        metadata.put("minimizedSample", Arrays.deepToString(minimized));
                        metadata.put("log", logger.getOutput());
                        var message = "Property failed from corpus with seed %d; sample=%s; minimized=%s"
                                .formatted(corpusSeed, Arrays.deepToString(generated), Arrays.deepToString(minimized));
                        return failedResult(
                                testClass,
                                method,
                                startedMillis,
                                elapsedMillis(startedNanos),
                                new AssertionError(message, unwrap(failure)),
                                metadata);
                    }
                }

                for (int attempt = 0; attempt < property.tries(); attempt++) {
                    long currentSeed = seed + attempt * 1_000_003L;
                    var random = new SplittableRandom(currentSeed);
                    Object[] generated = generatedArguments(method, random, currentSeed, attempt);
                    var logger = new DefaultTestLogger();
                    try {
                        invokePropertyOnce(testClass, plan, method, currentSeed, attempt, generated, logger);
                        lastLogger = logger;
                    } catch (Throwable failure) {
                        Object[] minimized = shrink(testClass, plan, method, currentSeed, attempt, generated);
                        corpus.addFailure(methodId, currentSeed);
                        metadata.put("failingAttempt", attempt);
                        metadata.put("sample", Arrays.deepToString(generated));
                        metadata.put("minimizedSample", Arrays.deepToString(minimized));
                        metadata.put("log", logger.getOutput());
                        var message = "Property failed with seed %d at attempt %d; sample=%s; minimized=%s"
                                .formatted(
                                        currentSeed,
                                        attempt,
                                        Arrays.deepToString(generated),
                                        Arrays.deepToString(minimized));
                        return failedResult(
                                testClass,
                                method,
                                startedMillis,
                                elapsedMillis(startedNanos),
                                new AssertionError(message, unwrap(failure)),
                                metadata);
                    }
                }
                Mocks.verifyStrictStubs();
                metadata.put("log", lastLogger.getOutput());
                return passedResult(testClass, method, startedMillis, startedNanos, metadata);
            } catch (Throwable error) {
                metadata.put("log", lastLogger.getOutput());
                return failedResult(
                        testClass, method, startedMillis, elapsedMillis(startedNanos), unwrap(error), metadata);
            }
        });
    }

    private void invokePropertyOnce(
            Class<?> testClass,
            TestPlan plan,
            Method method,
            long seed,
            int attempt,
            Object[] generated,
            DefaultTestLogger logger)
            throws Throwable {
        var invocation = newInvocation(testClass, method, seed, attempt, true, logger);
        try {
            invokeEach(plan.beforeEach(), invocation.instance(), invocation.container(), invocation.context());
            invokeBodyThenAfterEach(
                    method,
                    invocation,
                    plan.afterEach(),
                    resolveParameters(method, invocation.container(), invocation.context(), generated));
        } finally {
            invocation.container().close();
        }
    }

    // Runs the test body and then @AfterEach, preserving the *primary* failure: if the body fails and
    // cleanup also fails, the body's throwable is reported and the cleanup failure is attached as a
    // suppressed exception, rather than the cleanup failure masking the real cause.
    private void invokeBodyThenAfterEach(Method method, Invocation invocation, List<Method> afterEach, Object[] args)
            throws Throwable {
        Throwable primary = null;
        try {
            invoke(method, invocation.instance(), args);
        } catch (Throwable bodyError) {
            primary = bodyError;
        }
        try {
            invokeEach(afterEach, invocation.instance(), invocation.container(), invocation.context());
        } catch (Throwable cleanupError) {
            if (primary != null) {
                primary.addSuppressed(cleanupError);
            } else {
                primary = cleanupError;
            }
        }
        if (primary != null) {
            throw primary;
        }
    }

    private Object[] shrink(
            Class<?> testClass, TestPlan plan, Method method, long seed, int attempt, Object[] failing) {
        var current = failing.clone();
        var types = method.getParameterTypes();
        var genericTypes = method.getGenericParameterTypes();
        var annotations = method.getParameterAnnotations();
        int remainingTrials = MAX_SHRINK_TRIALS;
        boolean improved = true;
        while (improved && remainingTrials > 0) {
            improved = false;
            for (int i = 0; i < current.length && remainingTrials > 0; i++) {
                if (!hasAnnotation(annotations[i], ForAll.class)) {
                    continue;
                }
                for (Object candidate : candidatesFor(types[i], genericTypes[i], annotations[i], current[i])) {
                    if (remainingTrials-- <= 0) {
                        break;
                    }
                    var trial = current.clone();
                    trial[i] = candidate;
                    try {
                        invokePropertyOnce(testClass, plan, method, seed, attempt, trial, new DefaultTestLogger());
                    } catch (Throwable stillFails) {
                        current = trial;
                        improved = true;
                        break;
                    }
                }
                if (improved) break;
            }
        }
        return current;
    }

    private Object[] generatedArguments(Method method, SplittableRandom random, long seed, int attempt) {
        var types = method.getParameterTypes();
        var genericTypes = method.getGenericParameterTypes();
        var annotations = method.getParameterAnnotations();
        var args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (hasAnnotation(annotations[i], ForAll.class)) {
                var context = new GenerationContext(random, seed, attempt);
                var custom = customArbitrary(annotations[i]);
                args[i] = custom != null
                        ? custom.generate(context)
                        : BuiltInGenerators.generate(types[i], genericTypes[i], annotations[i], context);
            }
        }
        return args;
    }

    // Candidate values to try while shrinking a failing @ForAll argument: a custom generator's own
    // shrink() when one was supplied, otherwise the built-in type-based shrinker.
    private static List<Object> candidatesFor(Class<?> type, Type genericType, Annotation[] annotations, Object value) {
        var custom = customArbitrary(annotations);
        if (custom != null) {
            return new ArrayList<>(custom.shrink(value));
        }
        return Shrinker.candidates(type, genericType, annotations, value);
    }

    // Resolves the custom Arbitrary declared via @ForAll(gen = ...), or null when the built-ins apply.
    @SuppressWarnings("unchecked")
    private static su.kidoz.axiomj.property.Arbitrary<Object> customArbitrary(Annotation[] annotations) {
        var forAll = findAnnotation(annotations, ForAll.class);
        if (forAll == null || forAll.gen() == Object.class) {
            return null;
        }
        var genClass = forAll.gen();
        if (!su.kidoz.axiomj.property.Arbitrary.class.isAssignableFrom(genClass)) {
            throw new IllegalStateException(
                    "@ForAll(gen = " + genClass.getName() + ") must implement su.kidoz.axiomj.property.Arbitrary");
        }
        return (su.kidoz.axiomj.property.Arbitrary<Object>)
                ARBITRARY_GENERATORS.computeIfAbsent(genClass, TestRunner::instantiateArbitrary);
    }

    private static su.kidoz.axiomj.property.Arbitrary<?> instantiateArbitrary(Class<?> genClass) {
        try {
            var constructor = genClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (su.kidoz.axiomj.property.Arbitrary<?>) constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Could not instantiate @ForAll generator " + genClass.getName()
                            + " (it needs an accessible no-argument constructor)",
                    e);
        }
    }

    private Invocation newInvocation(
            Class<?> testClass, Method method, long seed, int attempt, boolean property, DefaultTestLogger logger)
            throws ReflectiveOperationException {
        var container = classRoot.child();
        container.bindInstance(su.kidoz.axiomj.api.TestLogger.class, logger);
        var context = new TestContext(
                testClass.getName() + "#" + method.getName(), displayName(method), seed, attempt, property);
        var instance = constructTest(testClass, container, context);
        injectFields(instance, container, context);
        return new Invocation(instance, container, context);
    }

    private SimpleContainer rootContainerFor(Class<?> testClass, List<String> activeProfiles)
            throws ReflectiveOperationException {
        var root = new SimpleContainer();
        var testClock = new su.kidoz.axiomj.api.TestClock();
        root.bindInstance(java.time.Clock.class, testClock);
        root.bindInstance(su.kidoz.axiomj.api.TestClock.class, testClock);

        var modules = testClass.getAnnotation(UseModules.class);
        if (modules != null) {
            for (Class<? extends TestModule> moduleType : modules.value()) {
                var profile = moduleType.getAnnotation(Profile.class);
                if (profile != null) {
                    boolean active = false;
                    for (String p : profile.value()) {
                        if (activeProfiles.contains(p)) {
                            active = true;
                            break;
                        }
                    }
                    if (!active) continue;
                }
                var module = moduleType.getDeclaredConstructor().newInstance();
                module.configure(root);
            }
        }
        if (testClass.isAnnotationPresent(AutoMock.class)) {
            root.setAutoMockFactory(type -> Mocks.mock(type, false));
        }
        var config = loadConfig(testClass);
        if (config != null) {
            root.bindInstance(Config.class, config);
        }
        return root;
    }

    private static Config loadConfig(Class<?> testClass) {
        var useConfig = testClass.getAnnotation(UseConfig.class);
        if (useConfig == null) {
            return null;
        }
        var properties = new Properties();
        var loader = testClass.getClassLoader();
        for (var path : useConfig.value()) {
            try (var in = loader.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("@UseConfig resource not found on the test classpath: " + path);
                }
                properties.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Could not load @UseConfig resource: " + path, e);
            }
        }
        return new Config(properties);
    }

    private Object constructTest(Class<?> testClass, SimpleContainer container, TestContext context)
            throws ReflectiveOperationException {
        Constructor<?> selected = Arrays.stream(testClass.getDeclaredConstructors())
                .filter(c -> c.isAnnotationPresent(Inject.class))
                .findFirst()
                .orElseGet(() -> Arrays.stream(testClass.getDeclaredConstructors())
                        .filter(c -> c.getParameterCount() == 0)
                        .findFirst()
                        .orElseGet(() -> Arrays.stream(testClass.getDeclaredConstructors())
                                .max(Comparator.comparingInt(Constructor::getParameterCount))
                                .orElseThrow()));
        selected.setAccessible(true);
        var args = resolveParameters(
                selected.getParameterTypes(),
                selected.getGenericParameterTypes(),
                selected.getParameterAnnotations(),
                container,
                context,
                null);
        return selected.newInstance(args);
    }

    private void injectFields(Object instance, SimpleContainer container, TestContext context)
            throws IllegalAccessException {
        Class<?> type = instance.getClass();
        while (type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (isInjectable(field)) {
                    field.setAccessible(true);
                    field.set(
                            instance,
                            resolveOne(
                                    field.getType(),
                                    field.getGenericType(),
                                    field.getAnnotations(),
                                    container,
                                    context,
                                    null));
                }
            }
            type = type.getSuperclass();
        }
    }

    private Object[] resolveParameters(
            Method method, SimpleContainer container, TestContext context, Object[] generated) {
        return resolveParameters(
                method.getParameterTypes(),
                method.getGenericParameterTypes(),
                method.getParameterAnnotations(),
                container,
                context,
                generated);
    }

    private Object[] resolveParameters(
            Class<?>[] types,
            Type[] genericTypes,
            Annotation[][] annotations,
            SimpleContainer container,
            TestContext context,
            Object[] generated) {
        var args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = resolveOne(
                    types[i],
                    genericTypes[i],
                    annotations[i],
                    container,
                    context,
                    generated == null ? null : generated[i]);
        }
        return args;
    }

    private Object resolveOne(
            Class<?> type,
            Type genericType,
            Annotation[] annotations,
            SimpleContainer container,
            TestContext context,
            Object generated) {
        if (hasAnnotation(annotations, ForAll.class)) {
            return generated;
        }
        var mock = findAnnotation(annotations, Mock.class);
        if (mock != null) {
            if (container.isAutoMockEnabled()) {
                return container.autoMockOf(type);
            }
            return Mocks.mock(type, mock.strict());
        }
        var named = findAnnotation(annotations, Named.class);
        if (named != null) {
            return container.get(type, named.value());
        }
        var value = findAnnotation(annotations, Value.class);
        if (value != null) {
            return resolveValue(type, value, container);
        }
        if (type == TestContext.class) {
            return context;
        }
        var collection = resolveCollection(type, genericType, container);
        if (collection != null) {
            return collection;
        }
        return container.get(type);
    }

    private static boolean isInjectable(Field field) {
        return field.isAnnotationPresent(Mock.class)
                || field.isAnnotationPresent(Inject.class)
                || field.isAnnotationPresent(Named.class)
                || field.isAnnotationPresent(Value.class);
    }

    private static Object resolveCollection(Class<?> type, Type genericType, SimpleContainer container) {
        if (type != List.class && type != Set.class && type != Collection.class) {
            return null;
        }
        if (!(genericType instanceof ParameterizedType parameterized)
                || !(parameterized.getActualTypeArguments()[0] instanceof Class<?> element)
                || !container.hasMultibindings(element)) {
            return null;
        }
        var all = container.getAll(element);
        return type == Set.class ? new LinkedHashSet<>(all) : all;
    }

    private static Object resolveValue(Class<?> type, Value value, SimpleContainer container) {
        var config = container.getIfBound(Config.class);
        if (config == null) {
            if (type.isRecord()) {
                throw new IllegalStateException("@Value(\"" + value.value() + "\") on record type requires @UseConfig");
            }
            if (Value.UNSET.equals(value.orElse())) {
                throw new IllegalStateException(
                        "@Value(\"" + value.value() + "\") requires @UseConfig on the test class");
            }
            return coerce(value.orElse(), type);
        }
        if (type.isRecord()) {
            return config.getRecord(value.value(), type);
        }
        String raw;
        if (config.find(value.value()).isPresent()) {
            raw = config.find(value.value()).get();
        } else if (!Value.UNSET.equals(value.orElse())) {
            raw = value.orElse();
        } else {
            throw new IllegalStateException("Missing config key for @Value(\"" + value.value() + "\")");
        }
        return coerce(raw, type);
    }

    private static Object coerce(String raw, Class<?> type) {
        if (type == String.class) {
            return raw;
        }
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(raw);
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(raw);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(raw);
        }
        if (type == double.class || type == Double.class) {
            return Double.parseDouble(raw);
        }
        throw new IllegalStateException("Unsupported @Value target type: " + type.getName());
    }

    private void invokeEach(List<Method> methods, Object instance, SimpleContainer container, TestContext context)
            throws Throwable {
        for (Method method : methods) {
            invoke(method, instance, resolveParameters(method, container, context, null));
        }
    }

    private void invokeAllStatic(List<Method> methods) throws Throwable {
        for (Method method : methods) {
            if (!Modifier.isStatic(method.getModifiers())) {
                throw new IllegalStateException("@BeforeAll/@AfterAll methods must be static in this MVP: " + method);
            }
            invoke(method, null, new Object[method.getParameterCount()]);
        }
    }

    private void invoke(Method method, Object instance, Object[] args) throws Throwable {
        try {
            method.setAccessible(true);
            method.invoke(instance, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static <A extends Annotation> A findAnnotation(Annotation[] annotations, Class<A> type) {
        for (var annotation : annotations) {
            if (type.isInstance(annotation)) {
                return type.cast(annotation);
            }
        }
        return null;
    }

    private static boolean hasAnnotation(Annotation[] annotations, Class<? extends Annotation> type) {
        for (var annotation : annotations) {
            if (type.isInstance(annotation)) {
                return true;
            }
        }
        return false;
    }

    private TestResult passedResult(
            Class<?> testClass, Method method, long startedMillis, long startedNanos, Map<String, Object> metadata) {
        return result(testClass, method, TestStatus.PASSED, startedMillis, elapsedMillis(startedNanos), null, metadata);
    }

    private TestResult failedResult(
            Class<?> testClass,
            Method method,
            long startedMillis,
            long durationMillis,
            Throwable error,
            Map<String, Object> metadata) {
        return result(testClass, method, TestStatus.FAILED, startedMillis, durationMillis, error, metadata);
    }

    private TestResult skippedResult(Class<?> testClass, Method method, String reason) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("skipReason", reason);
        metadata.put("thread", Thread.currentThread().getName());
        return result(testClass, method, TestStatus.SKIPPED, now(), 0, new SkipReason(reason), metadata);
    }

    private TestResult result(
            Class<?> testClass,
            Method method,
            TestStatus status,
            long startedMillis,
            long durationMillis,
            Throwable error,
            Map<String, Object> metadata) {
        var safeMetadata = new LinkedHashMap<String, Object>();
        if (metadata != null) safeMetadata.putAll(metadata);
        safeMetadata.putIfAbsent("thread", Thread.currentThread().getName());
        var descriptor = new TestDescriptor(
                id(testClass, method),
                testClass.getName(),
                method.getName(),
                displayName(method),
                tags(method),
                productArea(testClass, method),
                featureId(testClass, method),
                featureName(testClass, method),
                owner(testClass, method),
                scenario(testClass, method),
                requirements(testClass, method),
                SourceLocator.locate(sourceFile(testClass.getName()), method.getName()),
                dependsOn(method),
                order(method));
        return new TestResult(
                descriptor,
                status,
                startedMillis,
                startedMillis + Math.max(0, durationMillis),
                Math.max(0, durationMillis),
                error,
                safeMetadata);
    }

    private TestResult classFailureResult(String className, Throwable error) {
        long started = now();
        var descriptor = new TestDescriptor(
                className + "#<class>",
                className,
                "<class>",
                className,
                List.of(),
                "",
                "",
                "",
                "",
                "Class initialization",
                List.of(),
                SourceLocator.locate(sourceFile(className), "<class>"),
                List.of(),
                0);
        return new TestResult(
                descriptor,
                TestStatus.FAILED,
                started,
                started,
                0,
                error,
                Map.of("thread", Thread.currentThread().getName()));
    }

    private TestResult suiteFailureResult(String hook, Throwable error) {
        long started = now();
        var descriptor = new TestDescriptor(
                hook,
                hook,
                hook,
                hook,
                List.of(),
                "",
                "",
                "",
                "",
                "Suite lifecycle",
                List.of(),
                new SourceLocation("", 0, 0, 0),
                List.of(),
                0);
        return new TestResult(
                descriptor,
                TestStatus.FAILED,
                started,
                started,
                0,
                error,
                Map.of("thread", Thread.currentThread().getName()));
    }

    private void print(TestResult result) {
        var prefix =
                switch (result.status()) {
                    case PASSED -> "PASS";
                    case FAILED -> "FAIL";
                    case SKIPPED -> "SKIP";
                };
        out.printf(
                "[%s] %s :: %s (%d ms)%n",
                prefix, result.featureLabel(), result.displayName(), result.durationMillis());
        if (result.error() != null) {
            out.printf(
                    "  %s: %s%n",
                    result.error().getClass().getSimpleName(), result.error().getMessage());
        }
    }

    private static Map<String, Object> baseMetadata(long seed) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("seed", seed);
        metadata.put("thread", Thread.currentThread().getName());
        return metadata;
    }

    private static long timeoutMillis(Method method) {
        var fact = method.getAnnotation(Fact.class);
        if (fact != null) return fact.timeoutMillis();
        var property = method.getAnnotation(Property.class);
        return property == null ? 0 : property.timeoutMillis();
    }

    private static long stableSeed(long runSeed, Class<?> testClass, Method method) {
        long h = 1125899906842597L;
        var s = testClass.getName() + "#" + method.getName();
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
        }
        return h ^ runSeed;
    }

    private static String id(Class<?> testClass, Method method) {
        return testClass.getName() + "#" + method.getName();
    }

    private static String displayName(Method method) {
        var fact = method.getAnnotation(Fact.class);
        if (fact != null && !fact.name().isBlank()) return fact.name();
        var property = method.getAnnotation(Property.class);
        if (property != null && !property.name().isBlank()) return property.name();
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    private static List<String> tags(Method method) {
        var fact = method.getAnnotation(Fact.class);
        if (fact != null) return List.of(fact.tags());
        var property = method.getAnnotation(Property.class);
        if (property != null) return List.of(property.tags());
        return List.of();
    }

    private static List<String> dependsOn(Method method) {
        var dependencies = new ArrayList<String>();
        var fact = method.getAnnotation(Fact.class);
        if (fact != null) addAll(dependencies, fact.dependsOn());
        var property = method.getAnnotation(Property.class);
        if (property != null) addAll(dependencies, property.dependsOn());
        var dependsOn = method.getAnnotation(DependsOn.class);
        if (dependsOn != null) addAll(dependencies, dependsOn.value());
        return dependencies.stream().distinct().toList();
    }

    private static void addAll(List<String> target, String[] values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                target.add(value.trim());
            }
        }
    }

    private static int order(Method method) {
        var order = method.getAnnotation(Order.class);
        return order == null ? 0 : order.value();
    }

    private static String firstMissingDependency(TestNode node, Map<String, TestNode> nodes) {
        for (var dependency : node.dependsOn()) {
            if (!nodes.containsKey(dependency)) {
                return dependency;
            }
        }
        return null;
    }

    private static String firstFinishedNonPassingDependency(TestNode node, Map<String, TestResult> done) {
        for (var dependency : node.dependsOn()) {
            var result = done.get(dependency);
            if (result != null && !result.passed()) {
                return dependency;
            }
        }
        return null;
    }

    private static boolean dependenciesPassed(TestNode node, Map<String, TestResult> done) {
        for (var dependency : node.dependsOn()) {
            var result = done.get(dependency);
            if (result == null || !result.passed()) {
                return false;
            }
        }
        return true;
    }

    private static ExecutionMode executionMode(Class<?> type) {
        var execution = type.getAnnotation(Execution.class);
        return execution == null ? ExecutionMode.CONCURRENT : execution.value();
    }

    private static ExecutionMode executionMode(Method method) {
        var execution = method.getAnnotation(Execution.class);
        return execution == null ? ExecutionMode.CONCURRENT : execution.value();
    }

    private static boolean matchesFilters(RunConfig config, Class<?> testClass, Method method) {
        if (!config.featureFilters().isEmpty()) {
            var candidates = List.of(
                    productArea(testClass, method),
                    featureId(testClass, method),
                    featureName(testClass, method),
                    productArea(testClass, method) + "/" + featureId(testClass, method),
                    productArea(testClass, method) + " / " + featureId(testClass, method));
            boolean matched = false;
            for (var filter : config.featureFilters()) {
                for (var candidate : candidates) {
                    if (matchesFilter(candidate, filter)) {
                        matched = true;
                        break;
                    }
                }
                if (matched) break;
            }
            if (!matched) return false;
        }

        if (!config.tagFilters().isEmpty()) {
            var tags = tags(method);
            boolean matched = false;
            for (var filter : config.tagFilters()) {
                for (var tag : tags) {
                    if (matchesFilter(tag, filter)) {
                        matched = true;
                        break;
                    }
                }
                if (matched) break;
            }
            if (!matched) return false;
        }

        if (!config.ownerFilters().isEmpty()) {
            var owner = owner(testClass, method);
            boolean matched = false;
            for (var filter : config.ownerFilters()) {
                if (matchesFilter(owner, filter)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }

        if (!config.areaFilters().isEmpty()) {
            var area = productArea(testClass, method);
            boolean matched = false;
            for (var filter : config.areaFilters()) {
                if (matchesFilter(area, filter)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }

        if (!config.requirementFilters().isEmpty()) {
            var reqs = requirements(testClass, method);
            boolean matched = false;
            for (var filter : config.requirementFilters()) {
                for (var req : reqs) {
                    if (matchesFilter(req, filter)) {
                        matched = true;
                        break;
                    }
                }
                if (matched) break;
            }
            if (!matched) return false;
        }

        return true;
    }

    private static boolean matchesFilter(String candidate, String filter) {
        if (candidate == null || filter == null || filter.isBlank()) {
            return false;
        }
        var normalizedCandidate = candidate.toLowerCase(java.util.Locale.ROOT);
        var normalizedFilter = filter.toLowerCase(java.util.Locale.ROOT);
        if (normalizedFilter.endsWith("*")) {
            return normalizedCandidate.startsWith(normalizedFilter.substring(0, normalizedFilter.length() - 1));
        }
        return normalizedCandidate.equals(normalizedFilter);
    }

    private static String productArea(Class<?> testClass, Method method) {
        var methodFeature = method.getAnnotation(Feature.class);
        if (methodFeature != null && !methodFeature.area().isBlank()) return methodFeature.area();
        var methodArea = method.getAnnotation(ProductArea.class);
        if (methodArea != null && !methodArea.value().isBlank()) return methodArea.value();
        var classFeature = testClass.getAnnotation(Feature.class);
        if (classFeature != null && !classFeature.area().isBlank()) return classFeature.area();
        var classArea = testClass.getAnnotation(ProductArea.class);
        return classArea == null ? "" : classArea.value();
    }

    private static String featureId(Class<?> testClass, Method method) {
        var methodFeature = method.getAnnotation(Feature.class);
        if (methodFeature != null) return methodFeature.id();
        var classFeature = testClass.getAnnotation(Feature.class);
        return classFeature == null ? "" : classFeature.id();
    }

    private static String featureName(Class<?> testClass, Method method) {
        var methodFeature = method.getAnnotation(Feature.class);
        if (methodFeature != null) return methodFeature.name().isBlank() ? methodFeature.id() : methodFeature.name();
        var classFeature = testClass.getAnnotation(Feature.class);
        if (classFeature != null) return classFeature.name().isBlank() ? classFeature.id() : classFeature.name();
        return "";
    }

    private static String owner(Class<?> testClass, Method method) {
        var methodFeature = method.getAnnotation(Feature.class);
        if (methodFeature != null && !methodFeature.owner().isBlank()) return methodFeature.owner();
        var methodOwner = method.getAnnotation(Owner.class);
        if (methodOwner != null && !methodOwner.value().isBlank()) return methodOwner.value();
        var classFeature = testClass.getAnnotation(Feature.class);
        if (classFeature != null && !classFeature.owner().isBlank()) return classFeature.owner();
        var classOwner = testClass.getAnnotation(Owner.class);
        return classOwner == null ? "" : classOwner.value();
    }

    private static String scenario(Class<?> testClass, Method method) {
        var methodScenario = method.getAnnotation(Scenario.class);
        if (methodScenario != null && !methodScenario.value().isBlank()) return methodScenario.value();
        var classScenario = testClass.getAnnotation(Scenario.class);
        if (classScenario != null && !classScenario.value().isBlank()) return classScenario.value();
        return displayName(method);
    }

    private static List<String> requirements(Class<?> testClass, Method method) {
        var requirements = new ArrayList<String>();
        for (var requirement : testClass.getAnnotationsByType(Requirement.class)) {
            if (!requirement.value().isBlank()) requirements.add(requirement.value());
        }
        for (var requirement : method.getAnnotationsByType(Requirement.class)) {
            if (!requirement.value().isBlank()) requirements.add(requirement.value());
        }
        return requirements.stream().filter(Objects::nonNull).distinct().toList();
    }

    // Source roots searched to map a class to its source file. Defaults cover the standard Gradle Java/Kotlin
    // test layouts; override with -Daxiomj.sourceRoots=dir1,dir2 for custom or multi-module source sets.
    private static final List<String> SOURCE_ROOTS = sourceRoots();

    private static List<String> sourceRoots() {
        var configured = System.getProperty("axiomj.sourceRoots");
        if (configured == null || configured.isBlank()) {
            return List.of("src/test/java", "src/test/kotlin");
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(root -> !root.isEmpty())
                .toList();
    }

    private static String sourceFile(String className) {
        var topLevel = className.contains("$") ? className.substring(0, className.indexOf('$')) : className;
        var relative = topLevel.replace('.', '/');
        for (var root : SOURCE_ROOTS) {
            for (var extension : new String[] {".java", ".kt"}) {
                var candidate = root + "/" + relative + extension;
                if (java.nio.file.Files.isRegularFile(java.nio.file.Path.of(candidate))) {
                    return candidate;
                }
            }
        }
        // Nothing on disk matched (generated sources, different module, etc.) — fall back to the first root.
        return SOURCE_ROOTS.get(0) + "/" + relative + ".java";
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static void write(Path path, String content) throws IOException {
        var parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, content);
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof InvocationTargetException ite && ite.getCause() != null) {
            return ite.getCause();
        }
        if (error instanceof ExecutionException ee && ee.getCause() != null) {
            return ee.getCause();
        }
        return error;
    }

    private record Invocation(Object instance, SimpleContainer container, TestContext context) {}

    private record TestNode(Method method, List<String> dependsOn, int order) {}

    private record TestPlan(
            List<Method> beforeSuite,
            List<Method> beforeAll,
            List<Method> beforeEach,
            List<Method> tests,
            List<Method> afterEach,
            List<Method> afterAll,
            List<Method> afterSuite) {
        static TestPlan of(Class<?> testClass) {
            var beforeSuite = new ArrayList<Method>();
            var beforeAll = new ArrayList<Method>();
            var beforeEach = new ArrayList<Method>();
            var tests = new ArrayList<Method>();
            var afterEach = new ArrayList<Method>();
            var afterAll = new ArrayList<Method>();
            var afterSuite = new ArrayList<Method>();
            Class<?> type = testClass;
            var all = new ArrayList<Method>();
            while (type != Object.class) {
                all.addAll(Arrays.asList(type.getDeclaredMethods()));
                type = type.getSuperclass();
            }
            all.sort(Comparator.comparingInt(TestRunner::order).thenComparing(Method::getName));
            for (Method method : all) {
                if (method.isAnnotationPresent(su.kidoz.axiomj.api.BeforeSuite.class)) beforeSuite.add(method);
                if (method.isAnnotationPresent(BeforeAll.class)) beforeAll.add(method);
                if (method.isAnnotationPresent(BeforeEach.class)) beforeEach.add(method);
                if (method.isAnnotationPresent(Fact.class) || method.isAnnotationPresent(Property.class))
                    tests.add(method);
                if (method.isAnnotationPresent(AfterEach.class)) afterEach.add(method);
                if (method.isAnnotationPresent(AfterAll.class)) afterAll.add(method);
                if (method.isAnnotationPresent(su.kidoz.axiomj.api.AfterSuite.class)) afterSuite.add(method);
            }
            return new TestPlan(
                    List.copyOf(beforeSuite),
                    List.copyOf(beforeAll),
                    List.copyOf(beforeEach),
                    List.copyOf(tests),
                    List.copyOf(afterEach),
                    List.copyOf(afterAll),
                    List.copyOf(afterSuite));
        }
    }

    private static final class SkipReason extends RuntimeException {
        private SkipReason(String message) {
            super(message);
        }
    }
}
