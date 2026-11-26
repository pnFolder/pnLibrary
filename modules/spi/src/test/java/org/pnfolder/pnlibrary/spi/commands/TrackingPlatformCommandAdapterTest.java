package org.pnfolder.pnlibrary.spi.commands;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackingPlatformCommandAdapterTest {

    @Test
    void registrationCreatedWhileClosingIsReleasedAfterRegistration() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger closes = new AtomicInteger();
        TrackingPlatformCommandAdapter adapter = new TrackingPlatformCommandAdapter() {
            @Override
            protected PlatformCommandRegistration registerNative(
                    Object owner, CommandDefinition command, PlatformCommandDispatcher dispatcher) {
                entered.countDown();
                try {
                    assertTrue(release.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    throw new IllegalStateException(interrupted);
                }
                return closes::incrementAndGet;
            }
        };
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<PlatformCommandRegistration> future = executor.submit(() ->
                    adapter.register(new Object(), CommandDefinition.builder("test").build(), dispatcher()));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            adapter.close();
            release.countDown();

            future.get(2, TimeUnit.SECONDS);
            assertEquals(1, closes.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            adapter.close();
        }
    }

    @Test
    void closeThrowsTheFirstFailureAndKeepsTheOthersAsSuppressed() {
        TrackingPlatformCommandAdapter adapter = new TrackingPlatformCommandAdapter() {
            @Override
            protected PlatformCommandRegistration registerNative(
                    Object owner, CommandDefinition command, PlatformCommandDispatcher dispatcher) {
                return () -> {
                    throw new IllegalStateException("close failed for " + command.getName());
                };
            }
        };
        adapter.register(new Object(), CommandDefinition.builder("first").build(), dispatcher());
        adapter.register(new Object(), CommandDefinition.builder("second").build(), dispatcher());

        IllegalStateException thrown = assertThrows(IllegalStateException.class, adapter::close);

        assertEquals(1, thrown.getSuppressed().length);
    }

    private static PlatformCommandDispatcher dispatcher() {
        return new PlatformCommandDispatcher() {
            @Override
            public CompletionStage<Void> execute(CommandDefinition command, CommandContext context) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<List<String>> suggest(CommandDefinition command, CommandContext context) {
                return CompletableFuture.completedFuture(Collections.<String>emptyList());
            }
        };
    }
}
