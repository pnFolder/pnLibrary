package org.pnfolder.pnlibrary.common.concurrent;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Bounded, owned background worker. Closing drains accepted work within the close timeout, or fails every unfinished
 * promise and reports a timeout. A close from a worker callback initiates draining; an external close can subsequently
 * wait for it without deadlocking that callback.
 */
public final class ManagedWorker implements AutoCloseable {
    private final Object lock = new Object();
    private final Duration closeTimeout;
    private final ThreadLocal<Boolean> workerThread = new ThreadLocal<>();
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor executor;
    private boolean closed;

    /** A single-thread worker with room for 1024 queued operations and a 30 second close timeout. */
    public ManagedWorker(@NotNull String name) {
        this(name, 1);
    }

    /** A worker with {@code parallelism} threads and room for 1024 queued operations. */
    public ManagedWorker(@NotNull String name, int parallelism) {
        this(name, parallelism, 1024);
    }

    /** A worker with {@code parallelism} threads and a queue of {@code capacity} operations. */
    public ManagedWorker(@NotNull String name, int parallelism, int capacity) {
        this(name, parallelism, capacity, Duration.ofSeconds(30));
    }

    /**
     * A worker with {@code parallelism} threads, a queue of {@code capacity} operations, and a {@code closeTimeout}
     * for draining accepted work on close.
     *
     * @throws IllegalArgumentException when a count or the timeout is not positive
     */
    public ManagedWorker(@NotNull String name, int parallelism, int capacity, @NotNull Duration closeTimeout) {
        if (parallelism <= 0 || capacity <= 0) {
            throw new IllegalArgumentException("Worker parallelism and capacity must be positive");
        }
        if (closeTimeout.isNegative() || closeTimeout.isZero()) {
            throw new IllegalArgumentException("Worker close timeout must be positive");
        }
        this.closeTimeout = closeTimeout;
        this.executor = new ThreadPoolExecutor(parallelism, parallelism, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(capacity), task -> daemon(task, name));
    }

    /** How many accepted operations still wait for a free thread. */
    public int queued() {
        return executor.getQueue().size();
    }

    /**
     * Returns a promise for accepted work, or a failed promise when the bounded queue is full.
     *
     * @throws IllegalStateException when the worker is already closed
     */
    @NotNull
    public <T> CompletableFuture<T> submit(@NotNull Callable<T> action) {
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("Worker is closed");
            }
            CompletableFuture<T> promise = new CompletableFuture<>();
            pending.add(promise);
            promise.whenComplete((value, error) -> pending.remove(promise));
            try {
                executor.execute(() -> run(promise, action));
            } catch (RejectedExecutionException error) {
                promise.completeExceptionally(error);
            }
            return promise;
        }
    }

    /**
     * Rejects new work and waits up to the close timeout for accepted operations. It never throws, so it is safe
     * during plugin disable: work still unfinished at the timeout, or when the caller is interrupted, is abandoned
     * and its promises fail, which callers observe through the promises.
     */
    @Override
    public void close() {
        synchronized (lock) {
            if (!closed) {
                closed = true;
                executor.shutdown();
            }
        }
        if (Boolean.TRUE.equals(workerThread.get())) {
            return;
        }
        try {
            if (executor.awaitTermination(closeTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return;
            }
            abandon(new IllegalStateException("Background work did not finish within " + closeTimeout));
        } catch (InterruptedException error) {
            abandon(error);
            Thread.currentThread().interrupt();
        }
    }

    private <T> void run(CompletableFuture<T> promise, Callable<T> action) {
        workerThread.set(true);
        try {
            if (!promise.isDone()) {
                promise.complete(action.call());
            }
        } catch (Throwable error) {
            promise.completeExceptionally(error);
        } finally {
            workerThread.remove();
        }
    }

    private void abandon(Throwable error) {
        executor.shutdownNow();
        for (CompletableFuture<?> promise : new ArrayList<>(pending)) {
            promise.completeExceptionally(error);
        }
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }
}
