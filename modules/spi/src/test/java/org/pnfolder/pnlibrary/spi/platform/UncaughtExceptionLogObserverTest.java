package org.pnfolder.pnlibrary.spi.platform;

import org.pnfolder.pnlibrary.spi.platform.logging.UncaughtExceptionLogObserver;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.logging.LogLevel;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UncaughtExceptionLogObserverTest {
    @Test
    void observerFailureCannotPreventThePreviousHandlerFromSeeingTheException() {
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        AtomicReference<Throwable> received = new AtomicReference<>();
        IllegalStateException failure = new IllegalStateException("original");
        UncaughtExceptionLogObserver bridge = new UncaughtExceptionLogObserver(new Object());
        try {
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> received.set(error));
            bridge.observe((owner, level, message, error) -> {
                throw new IllegalStateException("observer failed");
            });
            assertThrows(IllegalStateException.class, () ->
                    Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), failure));
            assertSame(failure, received.get());
        } finally {
            bridge.observe(null);
            Thread.setDefaultUncaughtExceptionHandler(original);
        }
    }

    @Test
    void replacingObserverUsesTheLatestCallback() {
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        UncaughtExceptionLogObserver bridge = new UncaughtExceptionLogObserver(new Object());
        AtomicInteger oldCalls = new AtomicInteger();
        AtomicInteger newCalls = new AtomicInteger();
        try {
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> { });
            bridge.observe((owner, level, message, error) -> oldCalls.incrementAndGet());
            bridge.observe((owner, level, message, error) -> newCalls.incrementAndGet());
            Thread.getDefaultUncaughtExceptionHandler()
                    .uncaughtException(Thread.currentThread(), new IllegalStateException("test"));
            assertEquals(0, oldCalls.get());
            assertEquals(1, newCalls.get());
        } finally {
            bridge.observe(null);
            Thread.setDefaultUncaughtExceptionHandler(original);
        }
    }

    @Test
    void restoresPreviousHandlerAndReportsOwner() {
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        Thread.UncaughtExceptionHandler previous = (thread, error) -> { };
        Object owner = new Object();
        IllegalStateException error = new IllegalStateException("boom");
        AtomicReference<Object> receivedOwner = new AtomicReference<>();
        AtomicReference<LogLevel> receivedLevel = new AtomicReference<>();
        AtomicReference<Throwable> receivedError = new AtomicReference<>();

        try {
            Thread.setDefaultUncaughtExceptionHandler(previous);
            UncaughtExceptionLogObserver observer = new UncaughtExceptionLogObserver(owner);
            observer.observe((actualOwner, level, message, actualError) -> {
                receivedOwner.set(actualOwner);
                receivedLevel.set(level);
                receivedError.set(actualError);
            });

            Thread.UncaughtExceptionHandler installed = Thread.getDefaultUncaughtExceptionHandler();
            assertTrue(installed != null && installed != previous);
            installed.uncaughtException(Thread.currentThread(), error);

            observer.observe(null);
            assertSame(previous, Thread.getDefaultUncaughtExceptionHandler());
            assertSame(owner, receivedOwner.get());
            assertEquals(LogLevel.ERROR, receivedLevel.get());
            assertSame(error, receivedError.get());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original);
        }
    }
}
