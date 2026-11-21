package com.cae.clean_initializers.exceptions;

import com.cae.clean_mapped_exceptions.specifics.InternalMappedException;

/** Indicates that a lazy value's supplier failed during creation. */
public final class LazyInitializationMappedException extends InternalMappedException {
    /**
     * Retains the supplier's failure as the original cause.
     *
     * @param cause the supplier failure
     */
    public LazyInitializationMappedException(RuntimeException cause) {
        this(null, cause);
    }

    /**
     * Retains the original cause and records the lazy value's name and failure location.
     *
     * @param name the diagnostic name, or null for an unnamed value
     * @param cause the supplier failure, or null if unavailable
     */
    public LazyInitializationMappedException(String name, RuntimeException cause) {
        super("Lazy value could not be initialized", describeFailure(name, cause), cause);
    }

    private static String describeFailure(String name, RuntimeException cause) {
        String valueDescription = name == null ? "Lazy supplier" : "Supplier for lazy value '" + name + "'";
        if (cause != null) {
            StackTraceElement[] frames = cause.getStackTrace();
            if (frames.length > 0) {
                return valueDescription + " failed at " + frames[0];
            }
        }
        return valueDescription + " failed. Source location unavailable.";
    }
}
