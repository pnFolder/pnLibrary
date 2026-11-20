package org.pnfolder.pnlibrary.spi.platform

/** Lifecycle and thread-dispatch operations supplied by a native platform. */
interface PlatformExecution {
    /** Runs [task] on the platform's global (main) execution context. */
    fun executeGlobal(task: Runnable)
    /** Runs [task] on the context that owns [recipient], for example the region of a player. */
    fun executeReply(recipient: Any, task: Runnable)
}
