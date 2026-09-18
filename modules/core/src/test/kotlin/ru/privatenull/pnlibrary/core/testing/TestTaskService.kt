package ru.privatenull.pnlibrary.core.testing

import ru.privatenull.pnlibrary.api.tasks.TaskHandle
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.tasks.TaskService
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Supplier

/** Small deterministic scheduler used by event and plugin unit tests. */
internal class TestTaskService : TaskService {
    override fun scope(owner: Any): TaskScope = Scope(owner)
    override fun close(owner: Any) = Unit
    override fun close() = Unit

    private class Scope(override val owner: Any) : TaskScope {
        private val closed = AtomicBoolean(false)

        override fun global(task: Runnable): TaskHandle = runNow(task)
        override fun async(task: Runnable): TaskHandle {
            val handle = Handle()
            CompletableFuture.runAsync {
                if (!closed.get() && !handle.isCancelled) task.run()
            }
            return handle
        }

        override fun entity(recipient: Any, task: Runnable): TaskHandle = runNow(task)
        override fun later(delay: Duration, task: Runnable): TaskHandle = runNow(task)
        override fun laterEntity(recipient: Any, delay: Duration, task: Runnable): TaskHandle = runNow(task)
        override fun repeat(delay: Duration, interval: Duration, task: Runnable): TaskHandle = runNow(task)
        override fun repeatEntity(recipient: Any, delay: Duration, interval: Duration, task: Runnable): TaskHandle = runNow(task)
        override fun repeatAsync(delay: Duration, interval: Duration, task: Runnable): TaskHandle = async(task)

        override fun <T> asyncThen(
            work: Supplier<T>,
            success: Consumer<T>,
            failure: Consumer<Throwable>,
        ): TaskHandle = async(Runnable {
            runCatching { work.get() }.fold(success::accept, failure::accept)
        })

        override fun cancelAll() {
            closed.set(true)
        }

        private fun runNow(task: Runnable): TaskHandle {
            val handle = Handle()
            if (!closed.get()) task.run()
            return handle
        }
    }

    private class Handle : TaskHandle {
        private val cancelled = AtomicBoolean(false)
        override val isCancelled: Boolean get() = cancelled.get()
        override fun cancel() {
            cancelled.set(true)
        }
    }
}
