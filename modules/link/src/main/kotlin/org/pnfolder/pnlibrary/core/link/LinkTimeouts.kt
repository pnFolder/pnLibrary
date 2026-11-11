package org.pnfolder.pnlibrary.core.link

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Fires the timeouts of questions sent over the link.
 *
 * A [Link] has no close, and the platform makes a new one on every reload, so the thread must go away by itself:
 * it exits after a minute without work, and a timeout cancelled because the answer came is dropped at once.
 */
internal class LinkTimeouts(threadName: String) {
    private val timer = ScheduledThreadPoolExecutor(1) { task -> Thread(task, threadName).apply { isDaemon = true } }.apply {
        setKeepAliveTime(IDLE_SECONDS, TimeUnit.SECONDS)
        allowCoreThreadTimeOut(true)
        removeOnCancelPolicy = true
    }

    fun after(seconds: Long, action: () -> Unit): ScheduledFuture<*> = timer.schedule(Runnable { action() }, seconds, TimeUnit.SECONDS)

    private companion object {
        const val IDLE_SECONDS = 60L
    }
}
