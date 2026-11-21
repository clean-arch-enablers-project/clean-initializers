package com.cae.clean_initializers;

import com.cae.clean_initializers.exceptions.LazyInitializationMappedException;
import com.cae.clean_mapped_exceptions.specifics.InternalMappedException;

import java.util.function.Supplier;

/**
 * Creates a value on first access and retains it until reset.
 * Access and reset are serialized per initializer. Callers manage the lifetime and thread safety of the value.
 *
 * @param <T> the value type
 */
public final class Lazy<T> {
    private final String name;
    private final Supplier<? extends T> supplier;
    private T value;
    private volatile boolean initialized;
    private boolean initializing;

    private Lazy(String name, Supplier<? extends T> supplier) {
        if (supplier == null) {
            throw new InternalMappedException("A lazy value requires a supplier");
        }
        this.name = name;
        this.supplier = supplier;
    }

    /**
     * Declares a lazy value without calling its supplier.
     *
     * @param supplier the creation logic
     * @param <T> the value type
     * @return a new initializer
     * @throws InternalMappedException if the supplier is null
     */
    public static <T> Lazy<T> loadingOf(Supplier<? extends T> supplier) {
        return new Lazy<>(null, supplier);
    }

    /**
     * Declares a lazy value with a name used in initialization failure diagnostics.
     *
     * @param name a nonblank diagnostic name
     * @param supplier the creation logic
     * @param <T> the value type
     * @return a new initializer
     * @throws InternalMappedException if the name is null or blank, or the supplier is null
     */
    public static <T> Lazy<T> loadingOf(String name, Supplier<? extends T> supplier) {
        if (name == null || name.isBlank()) {
            throw new InternalMappedException("A named lazy value requires a nonblank name");
        }
        return new Lazy<>(name, supplier);
    }

    /**
     * Returns the retained value or creates it. A successful null result is retained.
     * A failed attempt leaves the next caller free to try again.
     *
     * @return the supplied value, which may be null
     * @throws LazyInitializationMappedException if the supplier throws a runtime exception
     * @throws InternalMappedException if the supplier calls get on this initializer during creation
     */
    public synchronized T get() {
        if (initialized) {
            return value;
        }
        if (initializing) {
            throw new InternalMappedException("A lazy supplier cannot access its own value during creation");
        }
        initializing = true;
        try {
            value = supplier.get();
            initialized = true;
            return value;
        } catch (RuntimeException failure) {
            throw new LazyInitializationMappedException(name, failure);
        } finally {
            initializing = false;
        }
    }

    /**
     * Reports whether creation has succeeded since construction or the most recent reset.
     * A creation attempt in progress has yet to initialize the value.
     *
     * @return whether this initializer retains a value
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Discards the retained value without disposing of it. An in-progress creation on
     * another thread completes before reset can proceed.
     *
     * @throws InternalMappedException if the supplier calls reset on this initializer during creation
     */
    public synchronized void reset() {
        if (initializing) {
            throw new InternalMappedException("A lazy supplier cannot reset its initializer during creation");
        }
        initialized = false;
        value = null;
    }
}
