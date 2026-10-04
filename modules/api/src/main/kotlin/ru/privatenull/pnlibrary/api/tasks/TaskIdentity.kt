package ru.privatenull.pnlibrary.api.tasks

import java.util.UUID

/** Stable non-blank identifier assigned to one scheduled task. */
data class TaskId(
    /** Non-blank identifier value. */
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Task id must not be blank" }
    }

    /** Returns the raw identifier [value]. */
    override fun toString(): String = value

    /** Creates task identifiers. */
    companion object {
        /** Returns a new UUID-backed task identifier. */
        @JvmStatic
        fun random(): TaskId = TaskId(UUID.randomUUID().toString())
    }
}

/** Current lifecycle state of a scheduled task. */
enum class TaskStatus {
    SCHEDULED,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED
}
/** Policy applied when a scope already contains the requested task key. */
enum class TaskConflictPolicy {
    REJECT,
    KEEP_EXISTING,
    REPLACE
}

/**
 * Platform execution context selected for a task.
 *
 * @property kind execution-context category
 * @property target platform-native entity for [Kind.ENTITY], otherwise `null`
 */
class TaskExecution private constructor(val kind: Kind, val target: Any?) {
    /** Supported execution-context categories. */
    enum class Kind {
        GLOBAL,
        ASYNC,
        ENTITY
    }
    /** Creates execution-context selections. */
    companion object {
        /** Selects the platform's global execution context. */
        @JvmStatic
        fun global() = TaskExecution(Kind.GLOBAL, null)

        /** Selects pnLibrary's asynchronous executor. */
        @JvmStatic
        fun async() = TaskExecution(Kind.ASYNC, null)

        /** Selects the execution context associated with native [target]. */
        @JvmStatic
        fun entity(target: Any) = TaskExecution(Kind.ENTITY, target)
    }
}
