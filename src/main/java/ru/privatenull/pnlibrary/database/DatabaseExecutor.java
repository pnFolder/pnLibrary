package ru.privatenull.pnlibrary.database;

import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Runs blocking storage work off-thread while preserving order within a logical lane. */
public final class DatabaseExecutor implements AutoCloseable {

    private final Plugin plugin;
    private final ExecutorService executor;
    private final Map<Object, CompletableFuture<Void>> lanes = new HashMap<>();
    private boolean closed;

    public DatabaseExecutor(Plugin plugin) {
        this(plugin, Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())));
    }

    public DatabaseExecutor(Plugin plugin, int threads) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        if (threads <= 0) throw new IllegalArgumentException("threads must be positive");
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, plugin.getName() + "-database-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newFixedThreadPool(threads, factory);
    }

    public <T> CompletableFuture<T> supply(Object lane, Supplier<T> operation) {
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(operation, "operation");
        CompletableFuture<T> result;
        CompletableFuture<Void> marker;
        synchronized (lanes) {
            ensureOpen();
            CompletableFuture<Void> previous = lanes.getOrDefault(lane, CompletableFuture.completedFuture(null));
            result = previous.handle((ignored, failure) -> null)
                    .thenApplyAsync(ignored -> operation.get(), executor);
            marker = result.handle((ignored, failure) -> null);
            lanes.put(lane, marker);
        }
        CompletableFuture<Void> expected = marker;
        marker.whenComplete((ignored, failure) -> {
            synchronized (lanes) {
                lanes.remove(lane, expected);
            }
        });
        return result;
    }

    public CompletableFuture<Void> run(Object lane, Runnable operation) {
        return supply(lane, () -> {
            operation.run();
            return null;
        });
    }

    public <T> void completeSync(
            CompletableFuture<T> future,
            Consumer<T> success,
            Consumer<Throwable> failure
    ) {
        Objects.requireNonNull(future, "future").whenComplete((value, throwable) -> {
            Runnable completion = () -> {
                if (throwable == null) success.accept(value);
                else failure.accept(unwrap(throwable));
            };
            if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, completion);
        });
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Database executor is closed");
    }

    @Override
    public void close() {
        synchronized (lanes) {
            if (closed) return;
            closed = true;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            synchronized (lanes) {
                lanes.clear();
            }
        }
    }
}
