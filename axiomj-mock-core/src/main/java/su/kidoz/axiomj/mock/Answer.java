package su.kidoz.axiomj.mock;

@FunctionalInterface
public interface Answer<T> {
    T answer(Invocation invocation) throws Throwable;
}
