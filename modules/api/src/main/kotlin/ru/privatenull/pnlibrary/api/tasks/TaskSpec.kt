package ru.privatenull.pnlibrary.api.tasks

import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.function.BooleanSupplier

/** Action invoked by a scheduled task. */
fun interface TaskAction {
    /** Performs one invocation using its live [context]. */
    fun run(context: TaskContext)
}

/** Live metadata and cancellation control exposed to a running task action. */
interface TaskContext {
    /** Stable identifier of the running task. */
    val id: TaskId
    /** Optional human-readable task name. */
    val name: String?
    /** One-based invocation number. */
    val runNumber: Long
    /** Time at which this invocation was intended to start. */
    val scheduledAt: Instant
    /** Time at which this invocation actually started. */
    val startedAt: Instant
    /** Cancels all future invocations of this task. */
    fun cancel()
}

/**
 * Immutable declaration used to schedule one task.
 *
 * @property name optional human-readable task name
 * @property key optional conflict-resolution key within an owner scope
 * @property conflictPolicy behavior when [key] is already registered
 * @property execution platform execution context
 * @property delay delay before the first invocation
 * @property interval repeat interval, or `null` for a one-shot task
 * @property conditions predicates that must all pass before an invocation runs
 * @property cancellationConditions predicates that cancel the task when any passes
 * @property tags immutable application-defined lookup tags
 * @property action callback invoked for each accepted run
 */
class TaskSpec private constructor(
    val name: String?,
    val key: String?,
    val conflictPolicy: TaskConflictPolicy,
    val execution: TaskExecution,
    val delay: Duration,
    val interval: Duration?,
    val conditions: List<BooleanSupplier>,
    val cancellationConditions: List<BooleanSupplier>,
    val tags: Set<String>,
    val action: TaskAction,
) {
    /** Fluent Java-friendly builder for a validated [TaskSpec]. */
    class Builder internal constructor() {
        private var name: String? = null
        private var key: String? = null
        private var conflictPolicy = TaskConflictPolicy.REJECT
        private var execution = TaskExecution.global()
        private var delay = Duration.ZERO
        private var interval: Duration? = null
        private val conditions = mutableListOf<BooleanSupplier>()
        private val cancellationConditions = mutableListOf<BooleanSupplier>()
        private val tags = linkedSetOf<String>()
        private var action: TaskAction? = null

        internal val configuredName: String? get() = name
        internal val configuredKey: String? get() = key
        internal val configuredConflictPolicy: TaskConflictPolicy get() = conflictPolicy
        internal val configuredExecution: TaskExecution get() = execution
        internal val configuredDelay: Duration get() = delay
        internal val configuredInterval: Duration? get() = interval
        internal val configuredTags: Set<String> get() = tags.toSet()

        /** Sets an optional human-readable task name. */
        fun name(value: String?) = apply { name = value }

        /** Sets an optional conflict-resolution key. */
        fun key(value: String?) = apply { key = value }

        /** Sets behavior for an existing task with the same key. */
        fun conflictPolicy(value: TaskConflictPolicy) = apply { conflictPolicy = value }

        /** Selects the platform execution context. */
        fun execution(value: TaskExecution) = apply { execution = value }

        /** Sets the delay before the first invocation. */
        fun delay(value: Duration) = apply { delay = value }

        /** Sets a repeat interval, or `null` for one-shot execution. */
        fun interval(value: Duration?) = apply { interval = value }

        /** Adds a predicate that must pass before an invocation runs. */
        fun condition(value: BooleanSupplier) = apply { conditions += value }

        /** Adds a predicate that permanently cancels the task when it passes. */
        fun cancelWhen(value: BooleanSupplier) = apply { cancellationConditions += value }

        /** Adds one non-blank lookup tag. */
        fun tag(value: String) = apply { tags += value }

        /** Adds all supplied lookup tags. */
        fun tags(values: Collection<String>) = apply { tags += values }

        /** Sets the required task callback. */
        fun action(value: TaskAction) = apply { action = value }

        /** Validates this declaration and creates an immutable task specification. */
        fun build(): TaskSpec {
            require(!delay.isNegative) { "Task delay must not be negative" }
            interval?.let { require(!it.isNegative && !it.isZero) { "Task interval must be positive" } }
            key?.let { require(it.isNotBlank()) { "Task key must not be blank" } }
            require(tags.none { it.isBlank() }) { "Task tags must not be blank" }
            return TaskSpec(name, key, conflictPolicy, execution, delay, interval,
                Collections.unmodifiableList(ArrayList(conditions)),
                Collections.unmodifiableList(ArrayList(cancellationConditions)),
                Collections.unmodifiableSet(LinkedHashSet(tags)),
                checkNotNull(action) { "Task action is required" })
        }
    }

    /** Creates task-specification builders. */
    companion object {
        /** Returns an empty Java-friendly task builder. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}

/** Kotlin property-based DSL for constructing a [TaskSpec]. */
class TaskSpecBuilder internal constructor() {
    private val delegate = TaskSpec.builder()
    /** Optional human-readable task name. */
    var name: String?
        get() = delegate.configuredName
        set(value) { delegate.name(value) }
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
    /** Repeat interval, or `null` for a one-shot task. */
    var interval: Duration?
        get() = delegate.configuredInterval
        set(value) { delegate.interval(value) }
    /** Application-defined lookup tags. */
    var tags: Set<String>
        get() = delegate.configuredTags
        set(value) { delegate.tags(value) }

    /** Adds a predicate that must pass before an invocation runs. */
    fun condition(test: () -> Boolean) { delegate.condition(BooleanSupplier(test)) }

    /** Adds a predicate that permanently cancels the task when it passes. */
    fun cancelWhen(test: () -> Boolean) { delegate.cancelWhen(BooleanSupplier(test)) }

    /** Sets the required task callback. */
    fun run(action: TaskAction) { delegate.action(action) }

    internal fun build(): TaskSpec = delegate.build()
}

/** Builds and schedules a task using the Kotlin receiver [configure]. */
@JvmSynthetic
fun TaskScope.schedule(configure: TaskSpecBuilder.() -> Unit): TaskHandle =
    schedule(TaskSpecBuilder().apply(configure).build())
