package org.pnfolder.pnlibrary.spi.platform

/** Server readiness scheduling boundary for work that must wait until native startup completes. */
interface PlatformReadiness {
    /** Runs [task] once native startup has completed. */
    fun whenServerReady(task: Runnable) = Unit
}
