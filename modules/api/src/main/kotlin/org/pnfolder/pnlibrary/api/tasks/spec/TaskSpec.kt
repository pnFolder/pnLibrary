package org.pnfolder.pnlibrary.api.tasks.spec

import org.pnfolder.pnlibrary.api.tasks.info.TaskLag
import org.pnfolder.pnlibrary.api.tasks.run.LagResponse
import org.pnfolder.pnlibrary.api.tasks.run.TaskLagHandler
import java.time.Duration
import java.util.Collections
import java.util.function.BooleanSupplier

/**
 * Immutable declaration used to schedule one task.
 *
 * @property name optional human-readable task name
 * @property description optional sentence on what the task is for, shown by `/pn tasks` and the menu
 * @property usage optional note on how the task is used or what it changes
 * @property cancellable whether an administrator may stop the task from `/pn tasks` or the menu; the owner can always cancel it
 * @property hidden whether the task is left out of `/pn tasks` and the menu; shown by default
 * @property runnableNow whether an administrator may start a waiting repeating task at once from `/pn tasks` or the menu
 * @property key optional conflict-resolution key within an owner scope
 * @property conflictPolicy behavior when [key] is already registered
 * @property execution platform execution context
 * @property delay delay before the first invocation
 * @property interval repeat interval, or `null` for a one-shot task
 * @property maxLag longest a run may start late (for example after the server froze) before it is skipped instead; `null` runs it anyway
 * @property tolerateFailures how many failures in a row a repeating task survives before it is stopped as failed; `0` stops it at the first
 * @property lagThreshold how late a run may start before [lagHandler] is asked about it
 * @property lagHandler reaction to a run that starts later than [lagThreshold], or `null` for none
 * @property cancelWithParent for a task started by another task: whether it is cancelled when that task is cancelled
 * @property conditions predicates that must all pass before an invocation runs
 * @property cancellationConditions predicates that cancel the task when any passes
 * @property tags immutable application-defined lookup tags
 * @property action callback invoked for each accepted run
 */
class TaskSpec private constructor(
    val name: String?,
    val description: String?,
    val usage: String?,
    val cancellable: Boolean,
    val runnableNow: Boolean,
    val hidden: Boolean,
    val key: String?,
    val conflictPolicy: TaskConflictPolicy,
    val execution: TaskExecution,
    val delay: Duration,
    val interval: Duration?,
    val maxLag: Duration?,
    val tolerateFailures: Int,
    val lagThreshold: Duration,
    val lagHandler: TaskLagHandler?,
    val cancelWithParent: Boolean,
    val conditions: List<BooleanSupplier>,
    val cancellationConditions: List<BooleanSupplier>,
    val tags: Set<String>,
    val action: TaskAction,
) {
    /** Fluent Java-friendly builder for a validated [TaskSpec]. */
    class Builder internal constructor() {
        private var name: String? = null
        private var description: String? = null
        private var usage: String? = null
        private var cancellable = true
        private var runnableNow = true
        private var hidden = false
        private var key: String? = null
        private var conflictPolicy = TaskConflictPolicy.REJECT
        private var execution = TaskExecution.global()
        private var delay = Duration.ZERO
        private var interval: Duration? = null
        private var maxLag: Duration? = null
        private var tolerateFailures = 0
        private var lagThreshold: Duration = DEFAULT_LAG_THRESHOLD
        private var lagHandler: TaskLagHandler? = null
        private var cancelWithParent = false
        private val conditions = mutableListOf<BooleanSupplier>()
        private val cancellationConditions = mutableListOf<BooleanSupplier>()
        private val tags = linkedSetOf<String>()
        private var action: TaskAction? = null

        internal val configuredName: String? get() = name
        internal val configuredDescription: String? get() = description
        internal val configuredUsage: String? get() = usage
        internal val configuredCancellable: Boolean get() = cancellable
        internal val configuredRunnableNow: Boolean get() = runnableNow
        internal val configuredHidden: Boolean get() = hidden
        internal val configuredKey: String? get() = key
        internal val configuredConflictPolicy: TaskConflictPolicy get() = conflictPolicy
        internal val configuredExecution: TaskExecution get() = execution
        internal val configuredDelay: Duration get() = delay
        internal val configuredInterval: Duration? get() = interval
        internal val configuredMaxLag: Duration? get() = maxLag
        internal val configuredTolerateFailures: Int get() = tolerateFailures
        internal val configuredLagThreshold: Duration get() = lagThreshold
        internal val configuredLagHandler: TaskLagHandler? get() = lagHandler
        internal val configuredCancelWithParent: Boolean get() = cancelWithParent
        internal val configuredTags: Set<String> get() = tags.toSet()

        /** Sets the optional human-readable task name. */
        fun name(value: String?): Builder = apply { name = value }

        /** Sets the optional sentence on what the task is for. */
        fun description(value: String?): Builder = apply { description = value }

        /** Sets the optional note on how the task is used or what it changes. */
        fun usage(value: String?): Builder = apply { usage = value }

        /** Sets whether an administrator may stop the task from the commands and the menu; `true` by default. */
        fun cancellable(value: Boolean): Builder = apply { cancellable = value }

        /** Sets whether an administrator may start the task at once from the commands and the menu; `true` by default. */
        fun runnableNow(value: Boolean): Builder = apply { runnableNow = value }

        /** Sets whether the task is left out of the commands and the menu; `false` by default. */
        fun hidden(value: Boolean): Builder = apply { hidden = value }

        /** Sets the conflict-resolution key within the owner scope; `null` means no key. */
        fun key(value: String?): Builder = apply { key = value }

        /** Sets the behavior when another live task already owns the key. */
        fun conflictPolicy(value: TaskConflictPolicy): Builder = apply { conflictPolicy = value }

        /** Selects the platform execution context. */
        fun execution(value: TaskExecution): Builder = apply { execution = value }

        /** Sets the delay before the first invocation. */
        fun delay(value: Duration): Builder = apply { delay = value }

        /**
         * Sets the delay before the first invocation in server ticks.
         *
         * One tick is defined as 50 milliseconds.
         */
        fun delayTicks(value: Long): Builder = apply { delay = ticksToDuration(value, "delay") }

        /** Sets the repeat interval; `null` makes the task one-shot. */
        fun interval(value: Duration?): Builder = apply { interval = value }

        /**
         * Sets a repeat interval in server ticks.
         *
         * One tick is defined as 50 milliseconds.
         */
        fun intervalTicks(value: Long): Builder = apply { interval = ticksToDuration(value, "interval") }

        /**
         * Skips a run that starts more than [value] after its planned time, such as after the server froze, instead
         * of running it late. A skipped run counts as skipped; a one-shot task that is too late simply ends. `null` (the
         * default) never skips.
         */
        fun maxLag(value: Duration?): Builder = apply { maxLag = value }

        /**
         * Lets a repeating task survive up to [value] failures in a row: each is logged and the task tries again at the
         * next interval. A successful run resets the count. `0` (the default) stops the task at the first failure.
         */
        fun tolerateFailures(value: Int): Builder = apply { tolerateFailures = value }

        /**
         * Calls [handler] right before a run that starts more than [threshold] after its planned time, such as after
         * the server froze, and lets it run or skip that run. Without a handler late runs simply run late.
         */
        fun onLag(threshold: Duration, handler: TaskLagHandler): Builder = apply {
            lagThreshold = threshold
            lagHandler = handler
        }

        /** Like [onLag] for a handler that only wants to be told: the late run always goes on. */
        fun whenLate(threshold: Duration, listener: java.util.function.Consumer<TaskLag>): Builder =
            onLag(threshold) { lag -> listener.accept(lag); LagResponse.RUN }

        /**
         * For a task started by another task: cancels it together with that task. Off by default, so a follow-up
         * outlives its parent.
         */
        fun cancelWithParent(value: Boolean): Builder = apply { cancelWithParent = value }

        /** Adds a predicate that must pass before each invocation runs. */
        fun condition(value: BooleanSupplier): Builder = apply { conditions += value }

        /** Adds a predicate that cancels the task as soon as it passes. */
        fun cancelWhen(value: BooleanSupplier): Builder = apply { cancellationConditions += value }

        /** Adds one lookup tag. */
        fun tag(value: String): Builder = apply { tags += value }

        /** Adds several lookup tags. */
        fun tags(values: Collection<String>): Builder = apply { tags += values }

        /** Sets the required callback invoked for each accepted run. */
        fun action(value: TaskAction): Builder = apply { action = value }

        /** Validates this declaration and creates an immutable task specification. */
        fun build(): TaskSpec {
            require(!delay.isNegative) { "Task delay must not be negative" }
            interval?.let { require(!it.isNegative && !it.isZero) { "Task interval must be positive" } }
            key?.let { require(it.isNotBlank()) { "Task key must not be blank" } }
            maxLag?.let { require(!it.isNegative && !it.isZero) { "Task max lag must be positive" } }
            require(tolerateFailures >= 0) { "Tolerated failures must not be negative" }
            require(!lagThreshold.isNegative) { "Task lag threshold must not be negative" }
            require(tags.none { it.isBlank() }) { "Task tags must not be blank" }
            return TaskSpec(name, description?.takeIf { it.isNotBlank() }, usage?.takeIf { it.isNotBlank() },
                cancellable, runnableNow, hidden, key, conflictPolicy, execution, delay, interval, maxLag, tolerateFailures,
                lagThreshold, lagHandler, cancelWithParent,
                Collections.unmodifiableList(ArrayList(conditions)),
                Collections.unmodifiableList(ArrayList(cancellationConditions)),
                Collections.unmodifiableSet(LinkedHashSet(tags)),
                checkNotNull(action) { "Task action is required" })
        }
    }

    companion object {
        private const val MILLIS_PER_TICK = 50L
        private val DEFAULT_LAG_THRESHOLD: Duration = Duration.ofSeconds(2)

        private fun ticksToDuration(value: Long, label: String): Duration {
            require(value >= 0) { "Task $label ticks must not be negative" }
            return Duration.ofMillis(Math.multiplyExact(value, MILLIS_PER_TICK))
        }

        /** Creates a new [Builder]; the entry point for Java callers. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
