package ru.privatenull.pnlibrary.spi.platform

/** Lifecycle and thread-dispatch operations supplied by a native platform. */
interface PlatformExecution {
    fun executeGlobal(task: Runnable)
    fun executeReply(recipient: Any, task: Runnable)
}
