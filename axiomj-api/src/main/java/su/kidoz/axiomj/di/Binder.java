package su.kidoz.axiomj.di;

import java.util.List;
import java.util.function.Supplier;

public interface Binder {
    <T> Binder bind(Class<T> type, Supplier<? extends T> provider);

    <T> Binder bind(Class<T> type, Supplier<? extends T> provider, Scope scope);

    <T> Binder bindInstance(Class<T> type, T instance);

    /** Registers a named (keyed) binding, selected with {@code @Named}. */
    <T> Binder bind(Class<T> type, String name, Supplier<? extends T> provider);

    <T> Binder bind(Class<T> type, String name, Supplier<? extends T> provider, Scope scope);

    <T> Binder bindInstance(Class<T> type, String name, T instance);

    /** Registers several providers for {@code type}, injectable together as a {@code List<T>}/{@code Set<T>}. */
    <T> Binder bindAll(Class<T> type, List<Supplier<? extends T>> providers);
}
