package ru.privatenull.pnlibrary.spi.platform

import kotlin.jvm.JvmSynthetic
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CopyOnWriteArrayList

/** Shared lifecycle state for a platform adapter: open, bound, and closed. */
class PlatformLifecycle {
    private val closed = AtomicBoolean(false)
    private val bound = AtomicBoolean(false)
    private val resources = CopyOnWriteArrayList<AutoCloseable>()

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
            try {
                closeTracked()?.let(error::addSuppressed)
            } finally {
                endBinding()
            }
            throw error
        }
    }

    @Synchronized
    fun endBinding() {
        bound.set(false)
    }

    /** Tracks a binding-owned resource so every adapter closes it through one lifecycle path. */
    @Synchronized
    fun <T : AutoCloseable> track(resource: T): T {
        checkOpen()
        resources += resource
        return resource
    }

    @Synchronized
    fun close(): Boolean {
        if (!closed.compareAndSet(false, true)) return false
        bound.set(false)
        closeTracked()?.let { throw it }
        return true
    }

    private fun closeTracked(): Throwable? {
        val failures = mutableListOf<Throwable>()
        resources.toList().asReversed().forEach { resource ->
            runCatching { resource.close() }.onFailure(failures::add)
        }
        resources.clear()
        val first = failures.firstOrNull() ?: return null
        failures.drop(1).forEach(first::addSuppressed)
        return first
    }
}
