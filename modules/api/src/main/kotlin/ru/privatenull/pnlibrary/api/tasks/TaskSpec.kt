package ru.privatenull.pnlibrary.api.tasks

import java.time.Duration
import java.time.Instant
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
                conditions.toList(), cancellationConditions.toList(), tags.toSet(),
                checkNotNull(action) { "Task action is required" })
        }
    }

    companion object { @JvmStatic fun builder(): Builder = Builder() }
}

class TaskSpecBuilder internal constructor() {
    private val delegate = TaskSpec.builder()
    var name: String? = null
    var key: String? = null
    var conflictPolicy: TaskConflictPolicy = TaskConflictPolicy.REJECT
    var execution: TaskExecution = TaskExecution.global()
    var delay: Duration = Duration.ZERO
    var interval: Duration? = null
    var tags: Set<String> = emptySet()
    private val conditions = mutableListOf<BooleanSupplier>()
    private val cancellationConditions = mutableListOf<BooleanSupplier>()
    private var action: TaskAction? = null
    fun condition(test: () -> Boolean) { conditions += BooleanSupplier(test) }
    fun cancelWhen(test: () -> Boolean) { cancellationConditions += BooleanSupplier(test) }
    fun run(action: TaskAction) { this.action = action }
    internal fun build(): TaskSpec {
        delegate.name(name).key(key).conflictPolicy(conflictPolicy).execution(execution)
            .delay(delay).interval(interval).tags(tags)
        conditions.forEach(delegate::condition)
        cancellationConditions.forEach(delegate::cancelWhen)
        return delegate.action(checkNotNull(action) { "Task action is required" }).build()
    }
}

fun TaskScope.schedule(configure: TaskSpecBuilder.() -> Unit): TaskHandle =
    schedule(TaskSpecBuilder().apply(configure).build())
