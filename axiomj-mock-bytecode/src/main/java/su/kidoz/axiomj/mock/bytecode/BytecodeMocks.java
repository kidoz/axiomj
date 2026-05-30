package su.kidoz.axiomj.mock.bytecode;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Modifier;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import su.kidoz.axiomj.mock.Mocks;

/**
 * Optional bytecode-backed mocking for concrete classes, complementing the interface-only engine in
 * {@code axiomj-mock-core}. A Byte Buddy subclass routes every overridable method to the core engine, so class mocks
 * get the full stubbing/verification surface ({@link Mocks#when}, {@link Mocks#verify}, {@link Mocks#given},
 * {@code inOrder}, captors, matchers, ...) for free.
 *
 * <p>Current scope and limitations:
 *
 * <ul>
 *   <li>Concrete (non-final) classes are supported; {@code final} classes cannot be subclassed.
 *   <li>{@code final}, {@code static}, and {@code private} methods are not overridable and therefore run their real
 *       implementation rather than being mocked.
 *   <li>The mock is created by invoking the class's accessible no-argument constructor, so that constructor's side
 *       effects run. Constructor-free instantiation (Objenesis) and static/constructor/private mocking via an
 *       instrumentation agent are planned follow-ups.
 * </ul>
 */
public final class BytecodeMocks {
    private BytecodeMocks() {}

    /** Creates a lenient class mock. */
    public static <T> T mockClass(Class<T> type) {
        return mockClass(type, false);
    }

    /** Creates a class mock; {@code strict} enables unused-stub detection like {@link Mocks#mock(Class, boolean)}. */
    public static <T> T mockClass(Class<T> type, boolean strict) {
        System.setProperty("net.bytebuddy.experimental", "true");
        if (type.isInterface()) {
            throw new IllegalArgumentException("Use Mocks.mock(...) for interfaces: " + type.getName());
        }
        if (Modifier.isFinal(type.getModifiers())) {
            throw new IllegalArgumentException("Cannot mock final class: " + type.getName());
        }
        InvocationHandler handler = Mocks.newControllerHandler(type, strict);
        Class<? extends T> subclass = new ByteBuddy()
                .subclass(type)
                .method(ElementMatchers.not(ElementMatchers.isStatic()))
                .intercept(InvocationHandlerAdapter.of(handler))
                .make()
                .load(classLoaderFor(type))
                .getLoaded();
        T instance = instantiate(subclass, type);
        Mocks.bindInstance(instance, handler);
        return instance;
    }

    private static <T> T instantiate(Class<? extends T> subclass, Class<T> type) {
        try {
            org.objenesis.Objenesis objenesis = new org.objenesis.ObjenesisStd();
            return objenesis.newInstance(subclass);
        } catch (Throwable e) {
            throw new IllegalStateException(
                    "Could not create class mock for " + type.getName() + " using Objenesis", e);
        }
    }

    private static ClassLoader classLoaderFor(Class<?> type) {
        var loader = type.getClassLoader();
        return loader != null ? loader : ClassLoader.getSystemClassLoader();
    }
}
