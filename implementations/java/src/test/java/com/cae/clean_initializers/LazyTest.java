package com.cae.clean_initializers;

import com.cae.clean_initializers.exceptions.LazyInitializationMappedException;
import com.cae.clean_mapped_exceptions.specifics.InternalMappedException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class LazyTest {
    @Test
    void defersCreationRetainsTheValueAndRecreatesItAfterReset() {
        var calls = new AtomicInteger();
        var lazy = Lazy.loadingOf(() -> {
            calls.incrementAndGet();
            return new Object();
        });

        assertFalse(lazy.isInitialized());
        lazy.reset();
        assertEquals(0, calls.get());

        var first = lazy.get();
        assertTrue(lazy.isInitialized());
        assertSame(first, lazy.get());
        assertEquals(1, calls.get());

        lazy.reset();
        assertFalse(lazy.isInitialized());
        assertNotSame(first, lazy.get());
        assertEquals(2, calls.get());
    }

    @Test
    void separateInitializersUsingTheSameSupplierRetainSeparateValues() {
        Supplier<Object> supplier = Object::new;
        var first = Lazy.loadingOf(supplier);
        var second = Lazy.loadingOf(supplier);

        var firstValue = first.get();
        assertFalse(second.isInitialized());
        assertNotSame(firstValue, second.get());
        assertSame(firstValue, first.get());
    }

    @Test
    void retainsNullAsASuccessfulValue() {
        var calls = new AtomicInteger();
        Lazy<String> lazy = Lazy.loadingOf(() -> {
            calls.incrementAndGet();
            return null;
        });

        assertNull(lazy.get());
        assertTrue(lazy.isInitialized());
        assertNull(lazy.get());
        assertEquals(1, calls.get());
        lazy.reset();
        assertFalse(lazy.isInitialized());
        assertNull(lazy.get());
        assertEquals(2, calls.get());
    }

    @Test
    void mapsTheOriginalFailureAndRetriesOnNextAccess() {
        var original = new IllegalStateException("Integration unavailable");
        var attempts = new AtomicInteger();
        var lazy = Lazy.loadingOf(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw original;
            }
            return "connected";
        });

        InternalMappedException failure = assertThrows(LazyInitializationMappedException.class, lazy::get);
        assertSame(original, failure.getCause());
        assertEquals("Lazy supplier failed at " + original.getStackTrace()[0], failure.getDetails().orElseThrow());
        assertFalse(lazy.isInitialized());
        assertEquals("connected", lazy.get());
        assertEquals("connected", lazy.get());
        assertEquals(2, attempts.get());
    }

    @Test
    void namedValueReportsWhereTheSupplierFailed() {
        var lazy = Lazy.loadingOf("billing-client", LazyTest::createUnavailableClient);

        var failure = assertThrows(LazyInitializationMappedException.class, lazy::get);
        var frame = failure.getCause().getStackTrace()[0];
        assertAll(
                () -> assertEquals("createUnavailableClient", frame.getMethodName()),
                () -> assertEquals("LazyTest.java", frame.getFileName()),
                () -> assertTrue(frame.getLineNumber() > 0),
                () -> assertEquals("Supplier for lazy value 'billing-client' failed at " + frame,
                        failure.getDetails().orElseThrow()),
                () -> assertEquals("Lazy value could not be initialized", failure.getBriefPublicMessage()));
    }

    @Test
    void keepsTheNameWhenTheOriginalFailureHasNoStackFrames() {
        var original = new IllegalStateException("Integration unavailable");
        original.setStackTrace(new StackTraceElement[0]);
        var lazy = Lazy.loadingOf("billing-client", () -> { throw original; });

        var failure = assertThrows(LazyInitializationMappedException.class, lazy::get);
        assertSame(original, failure.getCause());
        assertEquals("Supplier for lazy value 'billing-client' failed. Source location unavailable.",
                failure.getDetails().orElseThrow());
    }

    @Test
    void describesAnUnavailableLocationWhenNoCauseWasSupplied() {
        var failure = new LazyInitializationMappedException(null);
        assertNull(failure.getCause());
        assertEquals("Lazy supplier failed. Source location unavailable.", failure.getDetails().orElseThrow());
    }

    @Test
    void errorsPropagateAndReleaseTheInitializerForAnotherAttempt() {
        var error = new AssertionError("Supplier error");
        var attempts = new AtomicInteger();
        var lazy = Lazy.loadingOf(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw error;
            }
            return "recovered";
        });

        assertSame(error, assertThrows(AssertionError.class, lazy::get));
        assertFalse(lazy.isInitialized());
        assertEquals("recovered", lazy.get());
    }

    @Test
    void rejectsANullSupplierAsAnInternalError() {
        assertThrows(InternalMappedException.class, () -> Lazy.loadingOf(null));
    }

    @Test
    void rejectsNullAndBlankNamesForNamedValues() {
        assertThrows(InternalMappedException.class, () -> Lazy.loadingOf(null, Object::new));
        assertThrows(InternalMappedException.class, () -> Lazy.loadingOf("  ", Object::new));
    }

    @Test
    void rejectsRecursiveAccessAndResetDuringCreation() {
        var reference = new AtomicReference<Lazy<String>>();
        var recursive = Lazy.loadingOf(() -> reference.get().get());
        reference.set(recursive);
        var recursiveFailure = assertThrows(LazyInitializationMappedException.class, recursive::get);
        assertInstanceOf(InternalMappedException.class, recursiveFailure.getCause());
        assertFalse(recursive.isInitialized());

        var resetting = Lazy.loadingOf(() -> {
            reference.get().reset();
            return "never retained";
        });
        reference.set(resetting);
        var resetFailure = assertThrows(LazyInitializationMappedException.class, resetting::get);
        assertInstanceOf(InternalMappedException.class, resetFailure.getCause());
        assertFalse(resetting.isInitialized());
    }

    @Test
    void concurrentAccessSharesOneValue() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var lazy = Lazy.loadingOf(() -> {
            calls.incrementAndGet();
            entered.countDown();
            await(release);
            return new Object();
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(lazy::get);
            try {
                await(entered);
                assertFalse(lazy.isInitialized());
                var second = executor.submit(() -> {
                    secondStarted.countDown();
                    return lazy.get();
                });
                await(secondStarted);
                release.countDown();
                assertSame(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
                assertEquals(1, calls.get());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void concurrentResetClearsAnInFlightCreationAfterItCompletes() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var resetStarted = new CountDownLatch(1);
        var lazy = Lazy.loadingOf(() -> {
            entered.countDown();
            await(release);
            return new Object();
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var access = executor.submit(lazy::get);
            try {
                await(entered);
                var reset = executor.submit(() -> {
                    resetStarted.countDown();
                    lazy.reset();
                });
                await(resetStarted);
                release.countDown();
                var original = access.get(5, TimeUnit.SECONDS);
                reset.get(5, TimeUnit.SECONDS);
                assertFalse(lazy.isInitialized());
                assertNotSame(original, lazy.get());
            } finally {
                release.countDown();
            }
        }
    }

    private static String createUnavailableClient() {
        throw new IllegalStateException("Integration unavailable");
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Concurrent test did not reach its checkpoint");
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Concurrent test was interrupted", interruption);
        }
    }
}
