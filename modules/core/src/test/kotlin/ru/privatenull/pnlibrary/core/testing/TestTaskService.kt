package ru.privatenull.pnlibrary.core.testing

import ru.privatenull.pnlibrary.core.tasks.TaskService
import ru.privatenull.pnlibrary.api.tasks.TaskHandle
import ru.privatenull.pnlibrary.api.tasks.TaskContext
import ru.privatenull.pnlibrary.api.tasks.TaskId
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.tasks.TaskSpec
import ru.privatenull.pnlibrary.api.tasks.TaskAction
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** Small deterministic scheduler used by event and plugin unit tests. */
internal class TestTaskService : TaskService {
    override fun scope(owner: Any): TaskScope = Scope(owner)
    override fun close(owner: Any) = Unit
    override fun close() = Unit

    private class Scope(override val owner: Any) : TaskScope {
        private val closed = AtomicBoolean(false)

        override fun schedule(spec: TaskSpec): TaskHandle = runNow(spec.action)

        override fun cancelAll() {
            closed.set(true)
        }

        private fun runNow(task: TaskAction): TaskHandle {
            val handle = Handle()
            if (!closed.get()) task.run(object : TaskContext {
                override val id = TaskId("test-task")
                override val name: String? = null
                override val runNumber = 1L
                override val scheduledAt = Instant.EPOCH
                override val startedAt = Instant.EPOCH
                override fun cancel() = handle.cancel()
            })
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
