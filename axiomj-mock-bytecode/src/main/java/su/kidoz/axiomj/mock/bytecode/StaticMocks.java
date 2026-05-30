package su.kidoz.axiomj.mock.bytecode;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import su.kidoz.axiomj.mock.Mocks;

public final class StaticMocks {

    public static final Map<Class<?>, InvocationHandler> STATIC_HANDLERS = new ConcurrentHashMap<>();
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

        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.REDEFINITION)
                .with(AgentBuilder.Listener.StreamWriting.toSystemError())
                .type(ElementMatchers.is(type))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(StaticMockAdvice.class)
                                .on(ElementMatchers.isStatic().and(ElementMatchers.isMethod()))))
                .installOn(instrumentation);
    }

    public static void unmockStatic(Class<?> type) {
        STATIC_HANDLERS.remove(type);
        // Note: fully reverting the class transformation is complex in a quick prototype,
        // so we simply remove the handler. The advice will gracefully fall back to the real method.
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
                // Resolve the intercepted method. Prefer the exact descriptor; fall back to matching by
                // name and argument count so overloads are not confused (a substring match could bind to
                // the wrong method). If the fallback is still ambiguous, run the real method instead of
                // guessing.
                Method targetMethod = null;
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.toString().equals(methodId) || m.toGenericString().equals(methodId)) {
                        targetMethod = m;
                        break;
                    }
                }
                if (targetMethod == null) {
                    int argCount = args == null ? 0 : args.length;
                    for (Method m : clazz.getDeclaredMethods()) {
                        if (m.getParameterCount() == argCount && methodId.contains("." + m.getName() + "(")) {
                            if (targetMethod != null) {
                                targetMethod = null; // ambiguous: more than one candidate
                                break;
                            }
                            targetMethod = m;
                        }
                    }
                }
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
