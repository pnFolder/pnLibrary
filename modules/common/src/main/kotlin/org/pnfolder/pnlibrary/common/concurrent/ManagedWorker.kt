package org.pnfolder.pnlibrary.common.concurrent

import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Bounded, owned background worker. Closing drains accepted work within [closeTimeout], or fails
 * every unfinished promise and reports a timeout. A close from a worker callback initiates draining;
 * an external close can subsequently wait for it without deadlocking that callback.
 */
class ManagedWorker(
    name: String,
    parallelism: Int = 1,
    capacity: Int = 1024,
    private val closeTimeout: Duration = Duration.ofSeconds(30),
) : AutoCloseable {
    private val lock = Any()
    private var closed = false
    private val workerThread = ThreadLocal<Boolean>()
    private val pending = ConcurrentHashMap.newKeySet<CompletableFuture<*>>()
    private val executor: ThreadPoolExecutor

    init {
        require(parallelism > 0 && capacity > 0) { "Worker parallelism and capacity must be positive" }
        require(!closeTimeout.isNegative && !closeTimeout.isZero) { "Worker close timeout must be positive" }
        executor = ThreadPoolExecutor(parallelism, parallelism, 0, TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(capacity), { action -> Thread(action, name).apply { isDaemon = true } })
    }

    /** Returns a promise for accepted work, or a failed promise when the bounded queue is full. */
    fun <T> submit(action: () -> T): CompletableFuture<T> = synchronized(lock) {
        check(!closed) { "Worker is closed" }
        val promise = CompletableFuture<T>()
        pending.add(promise)
        promise.whenComplete { _, _ -> pending.remove(promise) }
        try {
            executor.execute {
                workerThread.set(true)
                try {
                    if (!promise.isDone) promise.complete(action())
                } catch (error: Throwable) {
                    promise.completeExceptionally(error)
                } finally { workerThread.remove() }
            }
        } catch (error: RejectedExecutionException) { promise.completeExceptionally(error) }
        promise
    }

    /** Rejects new work and waits for accepted operations; timeout/interruption is reported to callers. */
    override fun close() {
        synchronized(lock) {
            if (!closed) { closed = true; executor.shutdown() }
        }
        if (workerThread.get() == true) return
        try {
            if (executor.awaitTermination(closeTimeout.toMillis(), TimeUnit.MILLISECONDS)) return
            val error = IllegalStateException("Background work did not finish within $closeTimeout")
            executor.shutdownNow()
            pending.toList().forEach { it.completeExceptionally(error) }
            throw error
        } catch (error: InterruptedException) {
            executor.shutdownNow()
            pending.toList().forEach { it.completeExceptionally(error) }
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while draining background work", error)
        }
    }
}
