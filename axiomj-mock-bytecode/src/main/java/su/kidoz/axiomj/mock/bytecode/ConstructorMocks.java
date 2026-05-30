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

public final class ConstructorMocks {

    /** Internal SPI: read by the inlined {@link ConstructorAdvice} from instrumented classes. Not a stable API. */
    public static final Map<Class<?>, Boolean> MOCKED_CONSTRUCTIONS = new ConcurrentHashMap<>();

    private static final Map<Class<?>, ResettableClassFileTransformer> TRANSFORMERS = new ConcurrentHashMap<>();
    private static volatile Instrumentation instrumentation;

    private ConstructorMocks() {}

    private static synchronized void ensureAgentInstalled() {
        if (instrumentation == null) {
            System.setProperty("net.bytebuddy.experimental", "true");
            instrumentation = ByteBuddyAgent.install();
        }
    }

    public static void mockConstruction(Class<?> type) {
        mockConstruction(type, false);
    }

    public static void mockConstruction(Class<?> type, boolean strict) {
        ensureAgentInstalled();

        MOCKED_CONSTRUCTIONS.put(type, strict);
        // Scoped to the current test: the redefinition is reverted when the session ends (pass or fail).
        // The state map is process-wide, so concurrently mocking the *same* class from parallel tests races —
        // guard such tests with @Execution(SEQUENTIAL) or @ResourceLock on the mocked class.
        Mocks.onSessionEnd(() -> unmockConstruction(type));

        var transformer = new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.REDEFINITION)
                .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())
                .type(ElementMatchers.is(type))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder.visit(
                                Advice.to(ConstructorAdvice.class).on(ElementMatchers.isConstructor()))
                        .visit(Advice.to(InstanceMethodAdvice.class)
                                .on(ElementMatchers.isMethod().and(ElementMatchers.not(ElementMatchers.isStatic())))))
                .installOn(instrumentation);

        var previous = TRANSFORMERS.put(type, transformer);
        if (previous != null) {
            previous.reset(instrumentation, AgentBuilder.RedefinitionStrategy.REDEFINITION);
        }
    }

    public static void unmockConstruction(Class<?> type) {
        MOCKED_CONSTRUCTIONS.remove(type);
        var transformer = TRANSFORMERS.remove(type);
        if (transformer != null && instrumentation != null) {
            transformer.reset(instrumentation, AgentBuilder.RedefinitionStrategy.REDEFINITION);
        }
    }

    public static class ConstructorAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Origin Class<?> clazz, @Advice.This Object instance) {
            Boolean strict = MOCKED_CONSTRUCTIONS.get(clazz);
            if (strict != null) {
                InvocationHandler handler = Mocks.newControllerHandler(clazz, strict);
                Mocks.bindInstance(instance, handler);
            }
        }
    }

    public static class InstanceMethodAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean onEnter(
                @Advice.Origin Class<?> clazz,
                @Advice.This Object instance,
                @Advice.Origin String methodId,
                @Advice.AllArguments Object[] args,
                @Advice.Local("mockedResult") Object mockedResult)
                throws Throwable {

            InvocationHandler handler = Mocks.getHandlerIfMock(instance);
            if (handler != null) {
                Method targetMethod = MockMethodResolver.resolve(clazz, methodId, args);
                if (targetMethod != null) {
                    mockedResult = handler.invoke(instance, targetMethod, args);
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
