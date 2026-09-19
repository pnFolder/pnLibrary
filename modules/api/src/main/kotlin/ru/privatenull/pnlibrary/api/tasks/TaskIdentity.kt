package ru.privatenull.pnlibrary.api.tasks

import java.util.UUID

data class TaskId(val value: String) {
    init { require(value.isNotBlank()) { "Task id must not be blank" } }
    override fun toString(): String = value
    companion object { @JvmStatic fun random(): TaskId = TaskId(UUID.randomUUID().toString()) }
}

enum class TaskStatus { SCHEDULED, RUNNING, COMPLETED, CANCELLED, FAILED }
enum class TaskConflictPolicy { REJECT, KEEP_EXISTING, REPLACE }

class TaskExecution private constructor(val kind: Kind, val target: Any?) {
    enum class Kind { GLOBAL, ASYNC, ENTITY }
    companion object {
        @JvmStatic fun global() = TaskExecution(Kind.GLOBAL, null)
        @JvmStatic fun async() = TaskExecution(Kind.ASYNC, null)
        @JvmStatic fun entity(target: Any) = TaskExecution(Kind.ENTITY, target)
    }
}
