package ru.privatenull.pnlibrary.spi.platform

/** Server readiness scheduling boundary for work that must wait until native startup completes. */
interface PlatformReadiness {
    fun whenServerReady(task: Runnable) = Unit
}
