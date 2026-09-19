package ru.privatenull.pnlibrary.api.tasks

data class TaskServiceSettings(val historyCapacity: Int = 256) {
    init { require(historyCapacity >= 0) { "Task history capacity must not be negative" } }
}
