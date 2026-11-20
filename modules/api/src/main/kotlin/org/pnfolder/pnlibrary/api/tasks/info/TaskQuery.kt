package org.pnfolder.pnlibrary.api.tasks.info

import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution
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
    /** Returns whether [snapshot] satisfies every populated criterion of this query. */
    fun matches(snapshot: TaskSnapshot): Boolean =
        (id == null || snapshot.id == id) &&
            (key == null || snapshot.key == key) &&
            (nameContains == null || snapshot.name?.contains(nameContains, ignoreCase = true) == true) &&
            (statuses.isEmpty() || snapshot.status in statuses) &&
            (executionKinds.isEmpty() || snapshot.executionKind in executionKinds) &&
            snapshot.tags.containsAll(tags)

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

        /** Accepts only tasks in one of the given lifecycle states. */
        fun status(vararg value: TaskStatus): Builder = apply { statuses += value }

        /** Accepts only tasks with one of the given execution kinds. */
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
    companion object {
        /** Starts a new query builder. */
        @JvmStatic
        fun builder(): Builder = Builder()

        /** Returns a query matching every task. */
        @JvmStatic
        fun all(): TaskQuery = Builder().build()
    }
}
