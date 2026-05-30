package su.kidoz.axiomj.mock.bytecode;

import java.lang.reflect.Method;

/**
 * Resolves the {@link Method} an interception advice is firing for, shared by the static- and constructor-mock advices.
 *
 * <p>It prefers an exact match on the Byte Buddy {@code @Origin} descriptor, then falls back to matching by method name
 * and argument count; if that fallback is ambiguous (more than one candidate) it returns {@code null} so the caller
 * runs the real method rather than guessing. A substring match is deliberately avoided, since it can bind to the wrong
 * overload.
 */
public final class MockMethodResolver {
    private MockMethodResolver() {}

    public static Method resolve(Class<?> type, String methodId, Object[] args) {
        for (Method m : type.getDeclaredMethods()) {
            if (m.toString().equals(methodId) || m.toGenericString().equals(methodId)) {
                return m;
            }
        }
        int argCount = args == null ? 0 : args.length;
        Method candidate = null;
        for (Method m : type.getDeclaredMethods()) {
            if (m.getParameterCount() == argCount && methodId.contains("." + m.getName() + "(")) {
                if (candidate != null) {
                    return null; // ambiguous: more than one candidate, don't guess
                }
                candidate = m;
            }
        }
        return candidate;
    }
}
