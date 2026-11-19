package org.pnfolder.pnlibrary.api.tasks.spec

import org.pnfolder.pnlibrary.api.tasks.run.LagResponse
import org.pnfolder.pnlibrary.api.tasks.run.TaskContext
import org.pnfolder.pnlibrary.api.tasks.info.TaskLag
import java.time.Duration

/**
 * A task written as a class: the settings are overridable properties, the arguments are constructor parameters.
 *
 * It is the second way to describe a task next to [TaskSpec]: instead of a builder the task is its own type, so it
 * can hold its arguments, be tested alone and be scheduled anywhere with `scope.schedule(MyTask(...))`.
 * Every setting has the same meaning as the same-named field of [TaskSpec].
 *
 * ```kotlin
 * class Autosave(private val world: String) : ScheduledTask() {
 *     override val name = "Autosave $world"
 *     override val interval: Duration = Duration.ofMinutes(5)
 *     override val execution = TaskExecution.async()
 *
 *     override fun run(context: TaskContext) = save(world)
 * }
 *
 * tasks.schedule(Autosave("world"))
 * ```
 */
abstract class ScheduledTask : TaskAction {
    /** Human-readable task name. */
    open val name: String? get() = null

    /** What the task is for, shown by `/pn tasks`. */
    open val description: String? get() = null

    /** How the task is used or what it changes. */
    open val usage: String? get() = null

    /** Conflict-resolution key within the owner scope. */
    open val key: String? get() = null

    /** Behavior when [key] is already registered. */
    open val conflictPolicy: TaskConflictPolicy get() = TaskConflictPolicy.REJECT

    /** Platform execution context. */
    open val execution: TaskExecution get() = TaskExecution.global()

    /** Delay before the first invocation. */
    open val delay: Duration get() = Duration.ZERO

    /** Repeat interval, or `null` for a one-shot task. */
    open val interval: Duration? get() = null

    /** Longest a run may start late (for example after the server froze) before it is skipped; `null` never skips. */
    open val maxLag: Duration? get() = null

    /** Failures in a row a repeating task survives before it is stopped; `0` stops it at the first. */
    open val tolerateFailures: Int get() = 0

    /** How late a run may start before [onLag] is asked about it. */
    open val lagThreshold: Duration get() = Duration.ofSeconds(2)

    /** For a task started by another task: whether it is cancelled together with that task. */
    open val cancelWithParent: Boolean get() = false

    /** Lookup tags. */
    open val tags: Set<String> get() = emptySet()

    /** Whether an administrator may stop the task. */
    open val cancellable: Boolean get() = true

    /** Whether an administrator may start the task at once. */
    open val runnableNow: Boolean get() = true

    /** Whether the task is left out of the commands and the menu. */
    open val hidden: Boolean get() = false

    /**
     * Called right before a run that starts more than [lagThreshold] late, such as after the server froze. Override it
     * to tell an administrator or to skip a stale run; the default runs it anyway.
     */
    open fun onLag(lag: TaskLag): LagResponse = LagResponse.RUN

    /** Checked before each invocation; returning `false` skips that invocation. */
    open fun shouldRun(): Boolean = true

    /** Checked before each invocation; returning `true` cancels the task. */
    open fun shouldStop(): Boolean = false

    /** Builds the [TaskSpec] that describes this task as it is right now. */
    fun toSpec(): TaskSpec = TaskSpec.builder()
        .name(name)
        .description(description)
        .usage(usage)
        .key(key)
        .conflictPolicy(conflictPolicy)
        .execution(execution)
        .delay(delay)
        .interval(interval)
        .maxLag(maxLag)
        .tolerateFailures(tolerateFailures)
        .onLag(lagThreshold, ::onLag)
        .cancelWithParent(cancelWithParent)
        .tags(tags)
        .cancellable(cancellable)
        .runnableNow(runnableNow)
        .hidden(hidden)
        .condition(::shouldRun)
        .cancelWhen(::shouldStop)
        .action(this)
        .build()
}
