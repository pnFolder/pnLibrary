package ru.privatenull.pnlibrary.api.tasks

import java.time.Duration
import java.util.function.Consumer
import java.util.function.Supplier

/** Cancellation handle for one scheduled action. */
interface TaskHandle : AutoCloseable {
    val isCancelled: Boolean
    fun cancel()
    override fun close() = cancel()
}

/** Owner-bound group of tasks that are cancelled together on close. */
interface TaskScope : AutoCloseable {
    val owner: Any
    fun global(task: Runnable): TaskHandle
    fun async(task: Runnable): TaskHandle
    fun entity(recipient: Any, task: Runnable): TaskHandle
    fun later(delay: Duration, task: Runnable): TaskHandle
    fun laterEntity(recipient: Any, delay: Duration, task: Runnable): TaskHandle
    fun repeat(delay: Duration, interval: Duration, task: Runnable): TaskHandle
    fun repeatEntity(recipient: Any, delay: Duration, interval: Duration, task: Runnable): TaskHandle
    fun repeatAsync(delay: Duration, interval: Duration, task: Runnable): TaskHandle
    fun <T> asyncThen(work: Supplier<T>, success: Consumer<T>, failure: Consumer<Throwable>): TaskHandle
    fun cancelAll()
    override fun close() = cancelAll()
}

/** Cross-platform scheduling service with Folia-aware entity dispatch. */
interface TaskService : AutoCloseable {
    fun scope(owner: Any): TaskScope
    fun close(owner: Any)
    override fun close()
}
