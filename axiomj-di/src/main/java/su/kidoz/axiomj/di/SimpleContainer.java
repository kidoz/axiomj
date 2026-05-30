package su.kidoz.axiomj.di;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import su.kidoz.axiomj.api.Inject;

/**
 * Small hierarchical test container. A root container (per test class) holds the bindings declared by modules and
 * caches {@link Scope#SHARED}/{@link Scope#SINGLETON} instances; a {@link #child()} (per test) caches
 * {@link Scope#PER_TEST} instances and auto-mocks. {@link AutoCloseable} services are closed with their owning scope.
 * Supports default and named (keyed) bindings plus multi-bindings.
 */
public final class SimpleContainer implements Binder {
    private final SimpleContainer parent;
    private final Map<Key, Binding<?>> bindings = new HashMap<>();
    private final Map<Class<?>, List<Binding<?>>> multibindings = new HashMap<>();
    private final Map<Key, Object> instances = new HashMap<>();
    private final Map<Class<?>, Object> autoMocks = new HashMap<>();
    private final List<AutoCloseable> closeables = new ArrayList<>();
    private Function<Class<?>, Object> autoMockFactory;

    public SimpleContainer() {
        this(null);
    }

    private SimpleContainer(SimpleContainer parent) {
        this.parent = parent;
        this.autoMockFactory = parent == null ? null : parent.autoMockFactory;
    }

    /** Creates a per-test child of this container. */
    public SimpleContainer child() {
        return new SimpleContainer(this);
    }

    /** Installs the factory used to auto-mock unbound interfaces (set when {@code @AutoMock} is present). */
    public void setAutoMockFactory(Function<Class<?>, Object> factory) {
        this.autoMockFactory = factory;
    }

    public boolean isAutoMockEnabled() {
        return autoMockFactory != null;
    }

    @Override
    public <T> Binder bind(Class<T> type, Supplier<? extends T> provider) {
        return bind(type, null, provider, Scope.PER_TEST);
    }

    @Override
    public <T> Binder bind(Class<T> type, Supplier<? extends T> provider, Scope scope) {
        return bind(type, null, provider, scope);
    }

    @Override
    public <T> Binder bind(Class<T> type, String name, Supplier<? extends T> provider) {
        return bind(type, name, provider, Scope.PER_TEST);
    }

    @Override
    public <T> Binder bind(Class<T> type, String name, Supplier<? extends T> provider, Scope scope) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(scope, "scope");
        bindings.put(new Key(type, name), new Binding<>(provider, scope));
        return this;
    }

    @Override
    public <T> Binder bindInstance(Class<T> type, T instance) {
        return bindInstance(type, null, instance);
    }

    @Override
    public <T> Binder bindInstance(Class<T> type, String name, T instance) {
        bindings.put(new Key(type, name), new Binding<>(() -> instance, Scope.SINGLETON));
        return this;
    }

    @Override
    public <T> Binder bindAll(Class<T> type, List<Supplier<? extends T>> providers) {
        Objects.requireNonNull(type, "type");
        var list = multibindings.computeIfAbsent(type, _ -> new ArrayList<>());
        for (var provider : providers) {
            list.add(new Binding<>(provider, Scope.PER_TEST));
        }
        return this;
    }

    public <T> T get(Class<T> type) {
        return get(type, null);
    }

    public <T> T get(Class<T> type, String name) {
        Objects.requireNonNull(type, "type");
        return type.cast(resolve(type, name));
    }

    /** Returns the bound instance for {@code type}, or {@code null} if nothing is bound (no construct/auto-mock). */
    public <T> T getIfBound(Class<T> type) {
        var key = new Key(type, null);
        var binding = findBinding(key);
        return binding == null ? null : type.cast(resolveBinding(key, binding));
    }

    public boolean hasMultibindings(Class<?> elementType) {
        for (SimpleContainer current = this; current != null; current = current.parent) {
            if (current.multibindings.containsKey(elementType)) {
                return true;
            }
        }
        return false;
    }

    public <T> List<T> getAll(Class<T> elementType) {
        var result = new ArrayList<T>();
        for (SimpleContainer current = this; current != null; current = current.parent) {
            var list = current.multibindings.get(elementType);
            if (list != null) {
                for (var binding : list) {
                    result.add(elementType.cast(created(this, binding.provider.get())));
                }
            }
        }
        return result;
    }

    /**
     * Resolves (and caches per test) a shared auto-mock for {@code type}; used for {@code @Mock} under
     * {@code @AutoMock}.
     */
    public <T> T autoMockOf(Class<T> type) {
        return type.cast(autoMock(type));
    }

    private Object resolve(Class<?> type, String name) {
        var key = new Key(type, name);
        if (instances.containsKey(key)) {
            return instances.get(key);
        }
        var binding = findBinding(key);
        if (binding != null) {
            return resolveBinding(key, binding);
        }
        if (name != null) {
            throw new IllegalStateException("No binding for @Named(\"" + name + "\") " + type.getName());
        }
        if (type == SimpleContainer.class || type == Binder.class) {
            return this;
        }
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            if (autoMockFactory != null) {
                return autoMock(type);
            }
            throw new IllegalStateException(
                    "No binding for " + type.getName()
                            + ". Bind it in a TestModule, annotate the parameter/field with @Mock, or add @AutoMock to the test class.");
        }
        return construct(type);
    }

    private Binding<?> findBinding(Key key) {
        for (SimpleContainer current = this; current != null; current = current.parent) {
            var binding = current.bindings.get(key);
            if (binding != null) {
                return binding;
            }
        }
        return null;
    }

    private Object resolveBinding(Key key, Binding<?> binding) {
        return switch (binding.scope) {
            case TRANSIENT -> created(this, binding.provider.get());
            case PER_TEST -> {
                var instance = created(this, binding.provider.get());
                instances.put(key, instance);
                yield instance;
            }
            case SHARED, SINGLETON -> {
                var root = root();
                Object instance;
                synchronized (root) {
                    if (root.instances.containsKey(key)) {
                        instance = root.instances.get(key);
                    } else {
                        instance = created(root, binding.provider.get());
                        root.instances.put(key, instance);
                    }
                }
                if (this != root) {
                    this.instances.put(key, instance);
                }
                yield instance;
            }
        };
    }

    private synchronized Object autoMock(Class<?> type) {
        var existing = autoMocks.get(type);
        if (existing != null) {
            return existing;
        }
        var mock = autoMockFactory.apply(type);
        autoMocks.put(type, mock);
        return mock;
    }

    public <T> T construct(Class<T> type) {
        try {
            @SuppressWarnings("unchecked")
            var constructors = (Constructor<T>[]) type.getDeclaredConstructors();
            Constructor<T> selected = Arrays.stream(constructors)
                    .filter(c -> c.isAnnotationPresent(Inject.class))
                    .findFirst()
                    .orElseGet(() -> Arrays.stream(constructors)
                            .filter(c -> c.getParameterCount() == 0)
                            .findFirst()
                            .orElseGet(() -> Arrays.stream(constructors)
                                    .max(Comparator.comparingInt(Constructor::getParameterCount))
                                    .orElseThrow()));
            selected.setAccessible(true);
            var parameterTypes = selected.getParameterTypes();
            var args = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                args[i] = get(parameterTypes[i]);
            }
            var instance = selected.newInstance(args);
            created(this, instance);
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not construct " + type.getName(), e);
        }
    }

    /** Closes {@link AutoCloseable} services created in this container, most-recent first. Best effort. */
    public void close() {
        for (int i = closeables.size() - 1; i >= 0; i--) {
            try {
                Object closeable = closeables.get(i);
                if (closeable instanceof AutoCloseable) {
                    ((AutoCloseable) closeable).close();
                } else if (closeable instanceof java.util.concurrent.Future<?> f) {
                    // if a binding itself returns a future (like an async start), cancel it or wait.
                    // But typically the object itself has a method.
                    f.cancel(true);
                }
            } catch (Exception _) {
                // best-effort cleanup
            }
        }
        closeables.clear();
    }

    private SimpleContainer root() {
        var current = this;
        while (current.parent != null) {
            current = current.parent;
        }
        return current;
    }

    private static Object created(SimpleContainer owner, Object instance) {
        if (instance instanceof AutoCloseable closeable) {
            owner.closeables.add(closeable);
        }
        return instance;
    }

    private record Key(Class<?> type, String name) {}

    private static final class Binding<T> {
        private final Supplier<? extends T> provider;
        private final Scope scope;

        private Binding(Supplier<? extends T> provider, Scope scope) {
            this.provider = provider;
            this.scope = scope;
        }
    }
}
