package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.api.runtime.PnLibrary

/** Lifecycle and thread-dispatch operations supplied by a native platform. */
interface PlatformExecution {
    fun bind(library: PnLibrary) = Unit
    fun executeGlobal(task: Runnable)
    fun whenServerReady(task: Runnable) = executeGlobal(task)
    fun executeReply(recipient: Any, task: Runnable)
}
