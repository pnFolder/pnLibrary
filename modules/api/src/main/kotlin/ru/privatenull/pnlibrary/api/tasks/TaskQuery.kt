package ru.privatenull.pnlibrary.api.tasks

import java.time.Instant
import java.util.Collections

class TaskQuery private constructor(
    val id: TaskId?, val key: String?, val nameContains: String?,
    val statuses: Set<TaskStatus>, val executionKinds: Set<TaskExecution.Kind>, val tags: Set<String>,
) {
    class Builder internal constructor() {
        private var id: TaskId? = null
        private var key: String? = null
        private var nameContains: String? = null
        private val statuses = linkedSetOf<TaskStatus>()
        private val executionKinds = linkedSetOf<TaskExecution.Kind>()
        private val tags = linkedSetOf<String>()
        fun id(value: TaskId) = apply { id = value }
        fun key(value: String) = apply { key = value }
        fun nameContains(value: String) = apply { nameContains = value }
        fun status(vararg value: TaskStatus) = apply { statuses += value }
        fun execution(vararg value: TaskExecution.Kind) = apply { executionKinds += value }
        fun tag(value: String) = apply { tags += value }
        fun build(): TaskQuery {
            val normalizedKey = key?.trim()
            val normalizedName = nameContains?.trim()
            require(normalizedKey == null || normalizedKey.isNotEmpty()) { "Task query key must not be blank" }
            require(normalizedName == null || normalizedName.isNotEmpty()) { "Task query name must not be blank" }
            require(tags.none(String::isBlank)) { "Task query tags must not be blank" }
            return TaskQuery(
                id,
                normalizedKey,
                normalizedName,
                Collections.unmodifiableSet(LinkedHashSet(statuses)),
                Collections.unmodifiableSet(LinkedHashSet(executionKinds)),
                Collections.unmodifiableSet(LinkedHashSet(tags)),
            )
        }
    }
    companion object {
        @JvmStatic fun builder() = Builder()
        @JvmStatic fun all() = Builder().build()
    }
}

data class TaskSnapshot(
    val id: TaskId,
    val name: String?,
    val key: String?,
    val ownerName: String,
    val executionKind: TaskExecution.Kind,
    val status: TaskStatus,
    val tags: Set<String>,
    val createdAt: Instant,
    val nextRunAt: Instant?,
    val runCount: Long,
    val skippedCount: Long,
    val lastStartedAt: Instant?,
    val lastCompletedAt: Instant?,
    val lastFailure: String?,
)
