package ru.privatenull.pnlibrary.database

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class DatabaseRouter(val settings: DatabaseSettings) : Closeable {
    private val openFlag = AtomicBoolean(false)
    val isOpen: Boolean get() = openFlag.get()

    fun open() {
        if (!openFlag.compareAndSet(false, true)) return
    }

    override fun close() {
        if (openFlag.compareAndSet(true, false)) {
            // release connections
        }
    }
}
