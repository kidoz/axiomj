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

    private final PrintStream out;

    // Per-class root container, set while a class runs (classes are processed sequentially in run()).
    private volatile SimpleContainer classRoot;

    public TestRunner(PrintStream out) {
        this.out = out;
    }

    public RunSummary run(RunConfig config) throws IOException {
        var started = Instant.now();
        var results = new ArrayList<TestResult>();
        var threadFactory = Thread.ofVirtual().name("axiomj-test-", 0).factory();
        try (ExecutorService executor = Executors.newFixedThreadPool(config.parallelism(), threadFactory)) {
            for (String className : config.classNames()) {
                try {
                    Class<?> testClass = Class.forName(className);
                    var plan = TestPlan.of(testClass);
                    var root = rootContainerFor(testClass);
                    classRoot = root;
                    boolean beforeAllDone = false;
                    try {
                        invokeAllStatic(plan.beforeAll());
                        beforeAllDone = true;
                        results.addAll(runClass(config, executor, testClass, plan));
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
                    var result = classFailureResult(className, unwrap(error));
                    results.add(result);
                    print(result);
                }
            }
        }

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
        if (config.allureResultsDir() != null) {
            AllureReport.write(config.allureResultsDir(), results);
        }
        return summary;
    }

    private List<TestResult> runClass(RunConfig config, ExecutorService executor, Class<?> testClass, TestPlan plan) {
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
                    ? runSequential(config, testClass, plan, layer)
                    : runConcurrent(config, executor, testClass, plan, layer);
            for (var result : layerResults) {
                output.add(result);
                done.put(result.methodName(), result);
                remaining.remove(result.methodName());
                print(result);
            }
            changed = true;
        }
        return output;
    }

    private List<TestResult> runSequential(RunConfig config, Class<?> testClass, TestPlan plan, List<TestNode> nodes) {
        var results = new ArrayList<TestResult>();
        for (var node : nodes) {
            results.add(runMethod(testClass, plan, node.method(), config.seed()));
        }
        return results;
    }

    private List<TestResult> runConcurrent(
            RunConfig config, ExecutorService executor, Class<?> testClass, TestPlan plan, List<TestNode> nodes) {
        var results = new ArrayList<TestResult>();
        var futures = new LinkedHashMap<TestNode, Future<TestResult>>();
        for (var node : nodes) {
            futures.put(node, executor.submit(() -> runMethod(testClass, plan, node.method(), config.seed())));
        }
        for (var entry : futures.entrySet()) {
            var node = entry.getKey();
            var future = entry.getValue();
            try {
                results.add(future.get());
            } catch (Throwable error) {
                results.add(failedResult(testClass, node.method(), now(), 0, unwrap(error), Map.of()));
            }
        }
        return results;
    }

    private TestResult runMethod(Class<?> testClass, TestPlan plan, Method method, long runSeed) {
        long timeout = timeoutMillis(method);
        if (timeout <= 0) {
            return executeMethod(testClass, plan, method, runSeed);
        }
        // Enforce the timeout from the moment the test actually starts, independent of whether
        // it runs sequentially or concurrently and independent of the order futures are consumed.
        long startedMillis = now();
        long startedNanos = System.nanoTime();
        var future = new java.util.concurrent.CompletableFuture<TestResult>();
        var worker = Thread.ofVirtual().name("axiomj-timeout-", 0).start(() -> {
            try {
                future.complete(executeMethod(testClass, plan, method, runSeed));
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            worker.interrupt();
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

    private TestResult executeMethod(Class<?> testClass, TestPlan plan, Method method, long runSeed) {
        if (method.isAnnotationPresent(Property.class)) {
            return runProperty(testClass, plan, method, runSeed);
        }
        return runFact(testClass, plan, method, runSeed);
    }

    private TestResult runFact(Class<?> testClass, TestPlan plan, Method method, long runSeed) {
        long startedMillis = now();
        long startedNanos = System.nanoTime();
        var metadata = baseMetadata(runSeed);
        Mocks.openSession();
        SimpleContainer container = null;
        try {
            var invocation = newInvocation(testClass, method, runSeed, 0, false);
            container = invocation.container();
            invokeEach(plan.beforeEach(), invocation.instance(), invocation.container(), invocation.context());
            try {
                invoke(
                        method,
                        invocation.instance(),
                        resolveParameters(method, invocation.container(), invocation.context(), null));
            } finally {
                invokeEach(plan.afterEach(), invocation.instance(), invocation.container(), invocation.context());
            }
            Mocks.verifyStrictStubs();
            return passedResult(testClass, method, startedMillis, startedNanos, metadata);
        } catch (Throwable error) {
            return failedResult(testClass, method, startedMillis, elapsedMillis(startedNanos), unwrap(error), metadata);
        } finally {
            Mocks.closeSession();
            if (container != null) {
                container.close();
            }
        }
    }

    private TestResult runProperty(Class<?> testClass, TestPlan plan, Method method, long runSeed) {
        long startedMillis = now();
        long startedNanos = System.nanoTime();
        var property = method.getAnnotation(Property.class);
        long seed = property.seed() == Long.MIN_VALUE ? stableSeed(runSeed, testClass, method) : property.seed();
        var metadata = baseMetadata(seed);
        metadata.put("tries", property.tries());
        Mocks.openSession();
        try {
            for (int attempt = 0; attempt < property.tries(); attempt++) {
                var random = new SplittableRandom(seed + attempt * 1_000_003L);
                Object[] generated = generatedArguments(method, random, seed, attempt);
                try {
                    invokePropertyOnce(testClass, plan, method, seed, attempt, generated);
                } catch (Throwable failure) {
                    Object[] minimized = shrink(testClass, plan, method, seed, attempt, generated);
                    metadata.put("failingAttempt", attempt);
                    metadata.put("sample", Arrays.deepToString(generated));
                    metadata.put("minimizedSample", Arrays.deepToString(minimized));
                    var message = "Property failed with seed %d at attempt %d; sample=%s; minimized=%s"
                            .formatted(seed, attempt, Arrays.deepToString(generated), Arrays.deepToString(minimized));
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
            return passedResult(testClass, method, startedMillis, startedNanos, metadata);
        } catch (Throwable error) {
            return failedResult(testClass, method, startedMillis, elapsedMillis(startedNanos), unwrap(error), metadata);
        } finally {
            Mocks.closeSession();
        }
    }

    private void invokePropertyOnce(
            Class<?> testClass, TestPlan plan, Method method, long seed, int attempt, Object[] generated)
            throws Throwable {
        var invocation = newInvocation(testClass, method, seed, attempt, true);
        try {
            invokeEach(plan.beforeEach(), invocation.instance(), invocation.container(), invocation.context());
            try {
                invoke(
                        method,
                        invocation.instance(),
                        resolveParameters(method, invocation.container(), invocation.context(), generated));
            } finally {
                invokeEach(plan.afterEach(), invocation.instance(), invocation.container(), invocation.context());
            }
        } finally {
            invocation.container().close();
        }
    }

    private Object[] shrink(
            Class<?> testClass, TestPlan plan, Method method, long seed, int attempt, Object[] failing) {
        var current = failing.clone();
        var types = method.getParameterTypes();
        var annotations = method.getParameterAnnotations();
        int remainingTrials = MAX_SHRINK_TRIALS;
        boolean improved = true;
        while (improved && remainingTrials > 0) {
            improved = false;
            for (int i = 0; i < current.length && remainingTrials > 0; i++) {
                if (!hasAnnotation(annotations[i], ForAll.class)) {
                    continue;
                }
                for (Object candidate : Shrinker.candidates(types[i], annotations[i], current[i])) {
                    if (remainingTrials-- <= 0) {
                        break;
                    }
                    var trial = current.clone();
                    trial[i] = candidate;
                    try {
                        invokePropertyOnce(testClass, plan, method, seed, attempt, trial);
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
        var annotations = method.getParameterAnnotations();
        var args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (hasAnnotation(annotations[i], ForAll.class)) {
                args[i] = BuiltInGenerators.generate(
                        types[i], annotations[i], new GenerationContext(random, seed, attempt));
            }
        }
        return args;
    }

    private Invocation newInvocation(Class<?> testClass, Method method, long seed, int attempt, boolean property)
            throws ReflectiveOperationException {
        var container = classRoot.child();
        var context = new TestContext(
                testClass.getName() + "#" + method.getName(), displayName(method), seed, attempt, property);
        var instance = constructTest(testClass, container, context);
        injectFields(instance, container, context);
        return new Invocation(instance, container, context);
    }

    private SimpleContainer rootContainerFor(Class<?> testClass) throws ReflectiveOperationException {
        var root = new SimpleContainer();
        var modules = testClass.getAnnotation(UseModules.class);
        if (modules != null) {
            for (Class<? extends TestModule> moduleType : modules.value()) {
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
        String raw;
        if (config != null && config.find(value.value()).isPresent()) {
            raw = config.find(value.value()).get();
        } else if (!Value.UNSET.equals(value.orElse())) {
            raw = value.orElse();
        } else if (config == null) {
            throw new IllegalStateException("@Value(\"" + value.value() + "\") requires @UseConfig on the test class");
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
        var dependsBy = method.getAnnotation(DependsBy.class);
        if (dependsBy != null) addAll(dependencies, dependsBy.value());
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

    private static String sourceFile(String className) {
        var topLevel = className.contains("$") ? className.substring(0, className.indexOf('$')) : className;
        return "src/test/java/" + topLevel.replace('.', '/') + ".java";
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
            List<Method> beforeAll,
            List<Method> beforeEach,
            List<Method> tests,
            List<Method> afterEach,
            List<Method> afterAll) {
        static TestPlan of(Class<?> testClass) {
            var beforeAll = new ArrayList<Method>();
            var beforeEach = new ArrayList<Method>();
            var tests = new ArrayList<Method>();
            var afterEach = new ArrayList<Method>();
            var afterAll = new ArrayList<Method>();
            Class<?> type = testClass;
            var all = new ArrayList<Method>();
            while (type != Object.class) {
                all.addAll(Arrays.asList(type.getDeclaredMethods()));
                type = type.getSuperclass();
            }
            all.sort(Comparator.comparingInt(TestRunner::order).thenComparing(Method::getName));
            for (Method method : all) {
                if (method.isAnnotationPresent(BeforeAll.class)) beforeAll.add(method);
                if (method.isAnnotationPresent(BeforeEach.class)) beforeEach.add(method);
                if (method.isAnnotationPresent(Fact.class) || method.isAnnotationPresent(Property.class))
                    tests.add(method);
                if (method.isAnnotationPresent(AfterEach.class)) afterEach.add(method);
                if (method.isAnnotationPresent(AfterAll.class)) afterAll.add(method);
            }
            return new TestPlan(
                    List.copyOf(beforeAll),
                    List.copyOf(beforeEach),
                    List.copyOf(tests),
                    List.copyOf(afterEach),
                    List.copyOf(afterAll));
        }
    }

    private static final class SkipReason extends RuntimeException {
        private SkipReason(String message) {
            super(message);
        }
    }
}
