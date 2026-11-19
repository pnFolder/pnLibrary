package org.pnfolder.pnlibrary.api.tasks.spec

import org.pnfolder.pnlibrary.api.tasks.run.TaskLagHandler
import org.pnfolder.pnlibrary.api.tasks.run.TaskContext
import org.pnfolder.pnlibrary.api.tasks.TaskHandle
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.info.TaskLag
import java.time.Duration
import java.util.function.BooleanSupplier

/** Kotlin property-based DSL for constructing a [TaskSpec]. */
class TaskSpecBuilder internal constructor() {
    private val delegate = TaskSpec.builder()
    /** Optional human-readable task name. */
    var name: String?
        get() = delegate.configuredName
        set(value) { delegate.name(value) }
    /** Optional sentence on what the task is for. */
    var description: String?
        get() = delegate.configuredDescription
        set(value) { delegate.description(value) }
    /** Optional note on how the task is used or what it changes. */
    var usage: String?
        get() = delegate.configuredUsage
        set(value) { delegate.usage(value) }
    /** Whether an administrator may stop the task from the commands and the menu; `true` by default. */
    var cancellable: Boolean
        get() = delegate.configuredCancellable
        set(value) { delegate.cancellable(value) }
    /** Whether an administrator may start the task at once from the commands and the menu; `true` by default. */
    var runnableNow: Boolean
        get() = delegate.configuredRunnableNow
        set(value) { delegate.runnableNow(value) }
    /** Whether the task is left out of the commands and the menu; `false` by default. */
    var hidden: Boolean
        get() = delegate.configuredHidden
        set(value) { delegate.hidden(value) }
    /** Optional conflict-resolution key within the owner scope. */
    var key: String?
        get() = delegate.configuredKey
        set(value) { delegate.key(value) }
    /** Behavior when another live task already owns [key]. */
    var conflictPolicy: TaskConflictPolicy
        get() = delegate.configuredConflictPolicy
        set(value) { delegate.conflictPolicy(value) }
    /** Platform execution context used for invocations. */
    var execution: TaskExecution
        get() = delegate.configuredExecution
        set(value) { delegate.execution(value) }
    /** Delay before the first invocation. */
    var delay: Duration
        get() = delegate.configuredDelay
        set(value) { delegate.delay(value) }

    /** Sets the delay before the first invocation in server ticks (50 ms each). */
    fun delayTicks(value: Long) { delegate.delayTicks(value) }

    /** Repeat interval, or `null` for a one-shot task. */
    var interval: Duration?
        get() = delegate.configuredInterval
        set(value) { delegate.interval(value) }

    /** Sets the repeat interval in server ticks (50 ms each). */
    fun intervalTicks(value: Long) { delegate.intervalTicks(value) }

    /** Longest a run may start late before it is skipped; `null` never skips. See [TaskSpec.Builder.maxLag]. */
    var maxLag: Duration?
        get() = delegate.configuredMaxLag
        set(value) { delegate.maxLag(value) }

    /** Failures in a row a repeating task survives; `0` stops it at the first. See [TaskSpec.Builder.tolerateFailures]. */
    var tolerateFailures: Int
        get() = delegate.configuredTolerateFailures
        set(value) { delegate.tolerateFailures(value) }

    /** For a task started by another task: whether it is cancelled together with that task. Off by default. */
    var cancelWithParent: Boolean
        get() = delegate.configuredCancelWithParent
        set(value) { delegate.cancelWithParent(value) }

    /** Reacts when a run starts later than [threshold], for example after the server froze; see [TaskLagHandler]. */
    fun onLag(threshold: Duration, handler: TaskLagHandler) { delegate.onLag(threshold, handler) }

    /** Like [onLag] for a listener that only wants to be told: the late run always goes on. */
    fun whenLate(threshold: Duration, listener: (TaskLag) -> Unit) { delegate.whenLate(threshold) { listener(it) } }

    /** Adds one lookup tag. */
    fun tag(value: String) { delegate.tag(value) }

    /** Application-defined lookup tags. */
    var tags: Set<String>
        get() = delegate.configuredTags
        set(value) { delegate.tags(value) }

    /** Adds a predicate that must pass before each invocation runs. */
    fun condition(test: () -> Boolean) { delegate.condition(BooleanSupplier(test)) }

    /** Adds a predicate that cancels the task as soon as it passes. */
    fun cancelWhen(test: () -> Boolean) { delegate.cancelWhen(BooleanSupplier(test)) }

    /** Sets the callback invoked for each accepted run. */
    fun run(action: TaskAction) { delegate.action(action) }

    internal fun build(): TaskSpec = delegate.build()
}

/** Builds and schedules a task using the Kotlin receiver [configure]. */
@JvmSynthetic
fun TaskScope.schedule(configure: TaskSpecBuilder.() -> Unit): TaskHandle =
    schedule(TaskSpecBuilder().apply(configure).build())

/** Builds and starts a task of its own under the running task, using the Kotlin receiver [configure]. */
@JvmSynthetic
fun TaskContext.spawn(configure: TaskSpecBuilder.() -> Unit): TaskHandle =
    spawn(TaskSpecBuilder().apply(configure).build())
