package su.kidoz.axiomj.mock.bytecode;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import su.kidoz.axiomj.mock.Mocks;

public final class StaticMocks {

    /** Internal SPI: read by the inlined {@link StaticMockAdvice} from instrumented classes. Not a stable API. */
    public static final Map<Class<?>, InvocationHandler> STATIC_HANDLERS = new ConcurrentHashMap<>();

    private static final Map<Class<?>, ResettableClassFileTransformer> TRANSFORMERS = new ConcurrentHashMap<>();
    private static volatile Instrumentation instrumentation;

    private StaticMocks() {}

    private static synchronized void ensureAgentInstalled() {
        if (instrumentation == null) {
            System.setProperty("net.bytebuddy.experimental", "true");
            instrumentation = ByteBuddyAgent.install();
        }
    }

    public static void mockStatic(Class<?> type) {
        mockStatic(type, false);
    }

    public static void mockStatic(Class<?> type, boolean strict) {
        ensureAgentInstalled();

        InvocationHandler handler = Mocks.newControllerHandler(type, strict);
        STATIC_HANDLERS.put(type, handler);
        // Scope the static mock to the current test: when the test session ends (pass or fail) the handler is
        // removed automatically, so a failing test cannot leave a static mock active for later tests. Handlers
        // live in a process-wide map, so concurrently mocking the *same* class from parallel tests still races —
        // guard such tests with @Execution(SEQUENTIAL) or @ResourceLock on the mocked class.
        Mocks.onSessionEnd(() -> unmockStatic(type));

        var transformer = new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.REDEFINITION)
                .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())
                .type(ElementMatchers.is(type))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(StaticMockAdvice.class)
                                .on(ElementMatchers.isStatic().and(ElementMatchers.isMethod()))))
                .installOn(instrumentation);

        var previous = TRANSFORMERS.put(type, transformer);
        if (previous != null) {
            previous.reset(instrumentation, AgentBuilder.RedefinitionStrategy.REDEFINITION);
        }
    }

    public static void unmockStatic(Class<?> type) {
        STATIC_HANDLERS.remove(type);
        var transformer = TRANSFORMERS.remove(type);
        if (transformer != null && instrumentation != null) {
            transformer.reset(instrumentation, AgentBuilder.RedefinitionStrategy.REDEFINITION);
        }
    }

    public static class StaticMockAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean onEnter(
                @Advice.Origin Class<?> clazz,
                @Advice.Origin String methodId,
                @Advice.AllArguments Object[] args,
                @Advice.Local("mockedResult") Object mockedResult)
                throws Throwable {

            InvocationHandler handler = STATIC_HANDLERS.get(clazz);
            if (handler != null) {
                Method targetMethod = MockMethodResolver.resolve(clazz, methodId, args);
                if (targetMethod != null) {
                    mockedResult = handler.invoke(clazz, targetMethod, args);
                    return true; // Skip original method execution
                }
            }
            return false; // Proceed with original method
        }

        @Advice.OnMethodExit
        public static void onExit(
                @Advice.Enter boolean skipped,
                @Advice.Local("mockedResult") Object mockedResult,
                @Advice.Return(
                                readOnly = false,
                                typing = net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC)
                        Object returnValue) {
            if (skipped) {
                returnValue = mockedResult;
            }
        }
    }
}
