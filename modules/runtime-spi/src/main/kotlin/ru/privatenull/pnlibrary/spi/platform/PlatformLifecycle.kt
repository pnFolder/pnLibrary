package ru.privatenull.pnlibrary.spi.platform

import kotlin.jvm.JvmSynthetic
import java.util.concurrent.atomic.AtomicBoolean

/** Shared lifecycle state for a platform adapter: open, bound, and closed. */
class PlatformLifecycle {
    private val closed = AtomicBoolean(false)
    private val bound = AtomicBoolean(false)

    val isClosed: Boolean get() = closed.get()

    fun checkOpen() {
        check(!closed.get()) { "Platform adapter is closed" }
    }

    @Synchronized
    fun beginBinding() {
        checkOpen()
        check(bound.compareAndSet(false, true)) { "Platform adapter is already bound" }
    }

    /** Runs one binding transaction and rolls back the bound state if registration fails. */
    @JvmSynthetic
    @Synchronized
    fun <T> bind(block: () -> T): T {
        beginBinding()
        return try {
            block()
        } catch (error: Throwable) {
            endBinding()
            throw error
        }
    }

    @Synchronized
    fun endBinding() {
        bound.set(false)
    }

    @Synchronized
    fun close(): Boolean {
        if (!closed.compareAndSet(false, true)) return false
        bound.set(false)
        return true
    }
}
