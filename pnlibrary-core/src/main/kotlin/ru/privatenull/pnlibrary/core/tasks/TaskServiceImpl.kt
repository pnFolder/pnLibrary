package ru.privatenull.pnlibrary.core.tasks

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.tasks.TaskHandle
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.tasks.TaskService
import java.time.Duration
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Supplier

internal class TaskServiceImpl(
    private val platform: PlatformAdapter,
    private val errorLogger: (Any, String, Throwable) -> Unit = { owner, message, error ->
        platform.log(owner, LogLevel.ERROR, message, error)
    },
) : TaskService {
    private val executor = Executors.newScheduledThreadPool(4) { runnable ->
        Thread(runnable, "pnLibrary-tasks").apply { isDaemon = true }
    }
    private val scopes = Collections.synchronizedMap(IdentityHashMap<Any, Scope>())
    private val closed = AtomicBoolean(false)

    override fun scope(owner: Any): TaskScope {
        check(!closed.get()) { "TaskService is closed" }
        return synchronized(scopes) { scopes.getOrPut(owner) { Scope(owner) } }
    }

    override fun close(owner: Any) {
        synchronized(scopes) { scopes.remove(owner) }?.cancelAll()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val current = synchronized(scopes) { scopes.values.toList().also { scopes.clear() } }
        current.forEach { it.cancelAll() }
        executor.shutdownNow()
    }

    private inner class Scope(override val owner: Any) : TaskScope {
        private val handles = CopyOnWriteArraySet<Handle>()
        private val scopeClosed = AtomicBoolean(false)

        override fun global(task: Runnable) = once(Dispatch.GLOBAL, null, Duration.ZERO, task)
        override fun async(task: Runnable) = once(Dispatch.ASYNC, null, Duration.ZERO, task)
        override fun entity(recipient: Any, task: Runnable) = once(Dispatch.ENTITY, recipient, Duration.ZERO, task)
        override fun later(delay: Duration, task: Runnable) = once(Dispatch.GLOBAL, null, delay, task)
        override fun laterEntity(recipient: Any, delay: Duration, task: Runnable) =
            once(Dispatch.ENTITY, recipient, delay, task)

        override fun repeat(delay: Duration, interval: Duration, task: Runnable) =
            repeating(Dispatch.GLOBAL, null, delay, interval, task)

        override fun repeatEntity(recipient: Any, delay: Duration, interval: Duration, task: Runnable) =
            repeating(Dispatch.ENTITY, recipient, delay, interval, task)

        override fun repeatAsync(delay: Duration, interval: Duration, task: Runnable) =
            repeating(Dispatch.ASYNC, null, delay, interval, task)

        override fun <T> asyncThen(work: Supplier<T>, success: Consumer<T>, failure: Consumer<Throwable>): TaskHandle {
            ensureOpen()
            val handle = Handle()
            handles += handle
            val future = executor.submit {
                if (handle.isCancelled || scopeClosed.get()) {
                    handles.remove(handle)
                    return@submit
                }
                val continuation = runCatching { work.get() }.fold(
                    onSuccess = { value -> Runnable { success.accept(value) } },
                    onFailure = { error -> Runnable { failure.accept(error) } },
                )
                dispatch(Dispatch.GLOBAL, null, Runnable {
                    try {
                        guarded(handle, continuation).run()
                    } finally {
                        handles.remove(handle)
                    }
                })
            }
            handle.attach(future)
            return handle
        }

        private fun once(dispatch: Dispatch, recipient: Any?, delay: Duration, task: Runnable): Handle {
            ensureOpen()
            val handle = Handle()
            handles += handle
            val future = executor.schedule({
                if (!handle.isCancelled && !scopeClosed.get()) {
                    dispatch(dispatch, recipient, Runnable {
                        try { guarded(handle, task).run() } finally { handles.remove(handle) }
                    })
                } else {
                    handles.remove(handle)
                }
            }, millis(delay), TimeUnit.MILLISECONDS)
            handle.attach(future)
            return handle
        }

        private fun repeating(dispatch: Dispatch, recipient: Any?, delay: Duration, interval: Duration, task: Runnable): Handle {
            ensureOpen()
            require(!interval.isZero && !interval.isNegative) { "interval must be positive" }
            val handle = Handle()
            handles += handle
            val future = executor.scheduleAtFixedRate({
                if (!handle.isCancelled && !scopeClosed.get()) dispatch(dispatch, recipient, guarded(handle, task))
            }, millis(delay), millis(interval), TimeUnit.MILLISECONDS)
            handle.attach(future)
            return handle
        }

        private fun guarded(handle: Handle, task: Runnable) = Runnable {
            if (handle.isCancelled || scopeClosed.get()) return@Runnable
            runCatching { task.run() }.onFailure {
                errorLogger(owner, "[pnLibrary/tasks] Ошибка задачи ${owner.javaClass.simpleName}", it)
            }
        }

        private fun ensureOpen() = check(!scopeClosed.get() && !closed.get()) { "TaskScope is closed" }
        override fun cancelAll() {
            if (!scopeClosed.compareAndSet(false, true)) return
            handles.forEach { it.cancel() }
            handles.clear()
            synchronized(scopes) { if (scopes[owner] === this) scopes.remove(owner) }
        }
    }

    private fun dispatch(kind: Dispatch, recipient: Any?, task: Runnable) = when (kind) {
        Dispatch.GLOBAL -> platform.executeGlobal(task)
        Dispatch.ENTITY -> platform.executeReply(requireNotNull(recipient), task)
        Dispatch.ASYNC -> task.run()
    }

    private fun millis(duration: Duration): Long {
        require(!duration.isNegative) { "duration must not be negative" }
        return duration.toMillis()
    }

    private enum class Dispatch { GLOBAL, ENTITY, ASYNC }

    private class Handle : TaskHandle {
        private val cancelled = AtomicBoolean(false)
        @Volatile private var future: Future<*>? = null
        override val isCancelled: Boolean get() = cancelled.get()
        fun attach(value: Future<*>) {
            future = value
            if (cancelled.get()) value.cancel(false)
        }
        override fun cancel() {
            if (cancelled.compareAndSet(false, true)) future?.cancel(false)
        }
    }
}
