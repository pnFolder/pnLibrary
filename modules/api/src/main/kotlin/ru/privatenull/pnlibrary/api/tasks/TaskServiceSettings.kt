package ru.privatenull.pnlibrary.api.tasks

/**
 * Resource limits applied by a task service.
 *
 * @property historyCapacity maximum number of terminal task snapshots retained in memory
 */
data class TaskServiceSettings(val historyCapacity: Int = 256) {
    init { require(historyCapacity >= 0) { "Task history capacity must not be negative" } }
}
