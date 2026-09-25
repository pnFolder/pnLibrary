package ru.privatenull.pnlibrary.api.tasks

import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.function.BooleanSupplier

fun interface TaskAction { fun run(context: TaskContext) }

interface TaskContext {
    val id: TaskId
    val name: String?
    val runNumber: Long
    val scheduledAt: Instant
    val startedAt: Instant
    fun cancel()
}

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

        fun name(value: String?) = apply { name = value }
        fun key(value: String?) = apply { key = value }
        fun conflictPolicy(value: TaskConflictPolicy) = apply { conflictPolicy = value }
        fun execution(value: TaskExecution) = apply { execution = value }
        fun delay(value: Duration) = apply { delay = value }
        fun interval(value: Duration?) = apply { interval = value }
        fun condition(value: BooleanSupplier) = apply { conditions += value }
        fun cancelWhen(value: BooleanSupplier) = apply { cancellationConditions += value }
        fun tag(value: String) = apply { tags += value }
        fun tags(values: Collection<String>) = apply { tags += values }
        fun action(value: TaskAction) = apply { action = value }

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

    companion object { @JvmStatic fun builder(): Builder = Builder() }
}

class TaskSpecBuilder internal constructor() {
    private val delegate = TaskSpec.builder()
    var name: String?
        get() = delegate.configuredName
        set(value) { delegate.name(value) }
    var key: String?
        get() = delegate.configuredKey
        set(value) { delegate.key(value) }
    var conflictPolicy: TaskConflictPolicy
        get() = delegate.configuredConflictPolicy
        set(value) { delegate.conflictPolicy(value) }
    var execution: TaskExecution
        get() = delegate.configuredExecution
        set(value) { delegate.execution(value) }
    var delay: Duration
        get() = delegate.configuredDelay
        set(value) { delegate.delay(value) }
    var interval: Duration?
        get() = delegate.configuredInterval
        set(value) { delegate.interval(value) }
    var tags: Set<String>
        get() = delegate.configuredTags
        set(value) { delegate.tags(value) }
    fun condition(test: () -> Boolean) { delegate.condition(BooleanSupplier(test)) }
    fun cancelWhen(test: () -> Boolean) { delegate.cancelWhen(BooleanSupplier(test)) }
    fun run(action: TaskAction) { delegate.action(action) }
    internal fun build(): TaskSpec = delegate.build()
}

@JvmSynthetic
fun TaskScope.schedule(configure: TaskSpecBuilder.() -> Unit): TaskHandle =
    schedule(TaskSpecBuilder().apply(configure).build())
