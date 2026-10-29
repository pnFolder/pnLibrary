package org.pnfolder.pnlibrary.api.tasks

import java.time.Duration
import java.time.Instant
import java.util.Collections

/**
 * Immutable filter used to find live and retained task snapshots.
 *
 * @property id exact task identifier to match
 * @property key exact application-defined task key to match
 * @property nameContains case-insensitive name fragment to match
 * @property statuses accepted lifecycle states; empty accepts every state
 * @property executionKinds accepted execution kinds; empty accepts every kind
 * @property tags tags that matching tasks must contain
 */
class TaskQuery private constructor(
    val id: TaskId?,
    val key: String?,
    val nameContains: String?,
    val statuses: Set<TaskStatus>,
    val executionKinds: Set<TaskExecution.Kind>,
    val tags: Set<String>,
) {
    /** Fluent builder for a validated [TaskQuery]. */
    class Builder internal constructor() {
        private var id: TaskId? = null
        private var key: String? = null
        private var nameContains: String? = null
        private val statuses = linkedSetOf<TaskStatus>()
        private val executionKinds = linkedSetOf<TaskExecution.Kind>()
        private val tags = linkedSetOf<String>()

        /** Matches exactly [value]. */
        fun id(value: TaskId): Builder = apply { id = value }

        /** Matches the normalized application-defined task key. */
        fun key(value: String): Builder = apply { key = value }

        /** Matches task names containing [value]. */
        fun nameContains(value: String): Builder = apply { nameContains = value }

        /** Adds accepted lifecycle states. */
        fun status(vararg value: TaskStatus): Builder = apply { statuses += value }

        /** Adds accepted execution kinds. */
        fun execution(vararg value: TaskExecution.Kind): Builder = apply { executionKinds += value }

        /** Requires matching tasks to contain [value]. */
        fun tag(value: String): Builder = apply { tags += value }

        /** Validates and creates the immutable query. */
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
    /** Creates task queries. */
    companion object {
        /** Returns an empty query builder. */
        @JvmStatic
        fun builder(): Builder = Builder()

        /** Returns a query matching every task. */
        @JvmStatic
        fun all(): TaskQuery = Builder().build()
    }
}

/**
 * Immutable detached view of a live or retained task.
 *
 * @property id stable task identifier
 * @property name optional human-readable task name
 * @property key optional conflict-resolution key
 * @property ownerName diagnostic name of the owning object
 * @property executionKind selected execution context
 * @property status current or terminal lifecycle state
 * @property tags immutable application-defined tags
 * @property createdAt time at which the task was registered
 * @property nextRunAt next planned invocation, or `null` when none remains
 * @property runCount number of invocations that started
 * @property skippedCount number of invocations skipped because a condition rejected them
 * @property lastStartedAt most recent invocation start time
 * @property lastCompletedAt most recent successful completion time
 * @property lastFailure rendered most recent failure, when one occurred
 * @property description optional sentence on what the task is for
 * @property usage optional note on how the task is used
 * @property source class of the task action, as far as it can be told
 * @property interval repeat interval, or `null` for a one-shot task
 * @property lastDuration how long the latest finished invocation took
 * @property cancellable whether an administrator may stop the task from the commands and the menu
 * @property runnableNow whether an administrator may start the task at once from the commands and the menu
 */
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
    val description: String? = null,
    val usage: String? = null,
    val source: String? = null,
    val interval: Duration? = null,
    val lastDuration: Duration? = null,
    val cancellable: Boolean = true,
    val runnableNow: Boolean = true,
)
