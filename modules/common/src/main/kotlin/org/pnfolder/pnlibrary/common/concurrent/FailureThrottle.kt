package org.pnfolder.pnlibrary.common.concurrent

import java.util.concurrent.ConcurrentHashMap

/**
 * Lets a failure through the first time and then at most once per [intervalMillis] for the same key, so a loop that
 * keeps failing fills the log once instead of on every pass.
 */
class FailureThrottle(
    private val intervalMillis: Long = 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lastReported = ConcurrentHashMap<String, Long>()

    /** True when the failure with this [key] should be written now; later calls inside the interval return false. */
    fun shouldReport(key: String): Boolean {
        val now = clock()
        if (lastReported.size >= MAX_KEYS) lastReported.entries.removeIf { now - it.value >= intervalMillis }
        var allowed = false
        lastReported.compute(key) { _, last ->
            allowed = last == null || now - last >= intervalMillis
            if (allowed) now else last
        }
        return allowed
    }

    private companion object {
        const val MAX_KEYS = 256
    }
}
