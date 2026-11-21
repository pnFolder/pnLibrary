package org.pnfolder.pnlibrary.api.tasks.info

import java.util.UUID

/** Stable non-blank identifier assigned to one scheduled task. */
data class TaskId(
    /** Non-blank identifier value. */
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Task id must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        /** Returns a new UUID-backed task identifier. */
        @JvmStatic
        fun random(): TaskId = TaskId(UUID.randomUUID().toString())
    }
}
