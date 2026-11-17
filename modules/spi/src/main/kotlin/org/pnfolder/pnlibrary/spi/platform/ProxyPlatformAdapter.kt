package org.pnfolder.pnlibrary.spi.platform

import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.proxy.NetworkProxy

/**
 * Shared lifecycle of the proxy adapters: startup gate, native log observation, replies and shutdown.
 *
 * Startup-dependent callbacks wait for [markReady], called by the plugin entry point after runtime creation and
 * native API registration. Proxies have no per-player scheduler, so replies run as global work.
 *
 * @param owner identifies the uncaught-exception observer
 */
abstract class ProxyPlatformAdapter(owner: Any) : PlatformAdapter {

    abstract override val networkProxy: NetworkProxy

    /** Logs a warning through this adapter's log handler. */
    fun warn(message: String) = logHandler(this, LogLevel.WARNING, message, null)

    /** Registrations and shutdown state of this adapter. */
    protected val lifecycle = PlatformLifecycle()
    private val startup = StartupGate(lifecycle, ::executeGlobal)
    private val uncaughtExceptionObserver = UncaughtExceptionLogObserver(owner)

    /** Starts or stops forwarding the proxy's own and its plugins' log records to [observer]. */
    protected abstract fun observeProxyLogs(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?)

    /** Forwards proxy and plugin warnings/errors and uncaught JVM exceptions, preserving the previous handler. */
    @Synchronized
    override fun observeNativeLogs(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) {
        if (observer != null) lifecycle.checkOpen()
        observeProxyLogs(observer)
        uncaughtExceptionObserver.observe(observer)
    }

    /** Defers startup-dependent work until the entry point has published the runtime and native platform API. */
    override fun whenServerReady(task: Runnable) {
        startup.whenReady(task)
    }

    /** Releases startup callbacks once; other managed plugins may still use the startup grace. */
    fun markReady() {
        startup.markReady()
    }

    override fun executeReply(recipient: Any, task: Runnable) {
        executeGlobal(task)
    }

    /** Discards pending startup work, cancels native tasks and stops native log observation once. */
    override fun close() {
        lifecycle.closeAdapter(startup, taskAdapter) { observeNativeLogs(null) }
    }
}
