package org.pnfolder.pnlibrary.velocity

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.pnfolder.pnlibrary.api.logging.LogLevel

/**
 * Forwards WARN and ERROR events of Velocity's Log4j2 backend to the shared log observation contract.
 *
 * Velocity names a plugin's SLF4J logger after its main class, so an event is attributed to the plugin whose
 * main-class package is the longest prefix of the logger name; other events go to [fallbackOwner].
 * Events written through [runOwn] are skipped. When Log4j is unavailable the observer silently does nothing.
 */
internal class VelocityLogObserver(
    private val fallbackOwner: Any,
    private val pluginInstances: () -> Collection<Any>,
) {
    private val ownLogCall = ThreadLocal.withInitial { false }

    @Volatile
    private var callback: ((Any, LogLevel, String, Throwable?) -> Unit)? = null
    private var attached: Pair<LoggerContext, Appender>? = null

    /** Starts forwarding to [observer], or stops when it is null. */
    @Synchronized
    fun observe(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) {
        callback = observer
        if (observer != null && attached == null) attach() else if (observer == null) detach()
    }

    /** Runs [block] while events produced by this thread are treated as the library's own. */
    fun <T> runOwn(block: () -> T): T {
        val previous = ownLogCall.get()
        ownLogCall.set(true)
        try {
            return block()
        } finally {
            ownLogCall.set(previous)
        }
    }

    /** Returns the plugin whose main-class package is the longest prefix of [loggerName]. */
    internal fun resolveOwner(loggerName: String?): Any? {
        if (loggerName.isNullOrBlank()) return null
        return pluginInstances().mapNotNull { instance ->
            val prefix = instance.javaClass.name.substringBeforeLast('.', "")
            if (prefix.contains('.') && (loggerName == instance.javaClass.name || loggerName.startsWith("$prefix."))) {
                instance to prefix.length
            } else null
        }.maxByOrNull { it.second }?.first
    }

    private fun attach() {
        try {
            val context = LogManager.getContext(false) as? LoggerContext ?: return
            val appender = object : AbstractAppender(APPENDER_NAME, null, null, true, Property.EMPTY_ARRAY) {
                override fun append(event: LogEvent) = this@VelocityLogObserver.accept(event)
            }
            appender.start()
            context.configuration.rootLogger.addAppender(appender, Level.WARN, null)
            context.updateLoggers()
            attached = context to appender
        } catch (_: LinkageError) {
            // Log4j is not on the proxy class path; uncaught-exception observation still works.
        }
    }

    private fun detach() {
        val (context, appender) = attached ?: return
        attached = null
        context.configuration.rootLogger.removeAppender(APPENDER_NAME)
        context.updateLoggers()
        appender.stop()
    }

    private fun accept(event: LogEvent) {
        if (ownLogCall.get() || !event.level.isMoreSpecificThan(Level.WARN)) return
        val level = if (event.level.isMoreSpecificThan(Level.ERROR)) LogLevel.ERROR else LogLevel.WARNING
        val owner = resolveOwner(event.loggerName) ?: fallbackOwner
        callback?.invoke(owner, level, event.message?.formattedMessage ?: "Native platform error", event.thrown)
    }

    private companion object {
        const val APPENDER_NAME = "pnLibrary-log-observer"
    }
}
