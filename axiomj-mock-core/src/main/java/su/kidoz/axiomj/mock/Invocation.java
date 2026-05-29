package su.kidoz.axiomj.mock;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public record Invocation(Class<?> mockType, Method method, List<Object> arguments) {
    static Invocation from(Class<?> mockType, Method method, Object[] args) {
        Object[] copy = args == null ? new Object[0] : args.clone();
        return new Invocation(mockType, method, Arrays.asList(copy));
    }

    public String signature() {
        return mockType.getSimpleName() + "." + method.getName() + arguments;
    }
}
