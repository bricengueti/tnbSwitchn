package TNB.Switch.annotation;

@FunctionalInterface
public interface ThrowingSupplier<T> {
    T get() throws Throwable;
}