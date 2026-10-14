package ru.privatenull.pnlibrary.spi.platform

import java.util.concurrent.atomic.AtomicBoolean

/** Shared lifecycle state for a platform adapter: open, bound, and closed. */
class PlatformLifecycle {
    private val closed = AtomicBoolean(false)
    private val bound = AtomicBoolean(false)

    val isClosed: Boolean get() = closed.get()

    fun checkOpen() {
        check(!closed.get()) { "Platform adapter is closed" }
    }

    fun beginBinding() {
        checkOpen()
        check(bound.compareAndSet(false, true)) { "Platform adapter is already bound" }
    }

    fun endBinding() {
        bound.set(false)
    }

    fun close(): Boolean = closed.compareAndSet(false, true)
}
