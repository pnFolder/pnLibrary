package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.api.logging.LogLevel
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Forwards WARNING and SEVERE records of a `java.util.logging` tree to the shared log observation contract.
 *
 * Plugin loggers are children of the platform logger, so one handler on [platformLogger] sees every
 * plugin's output. The source plugin is resolved from the record's logger name through [resolveOwner];
 * unmatched records are attributed to [fallbackOwner]. Records written through [runOwn] are skipped so
 * the library's own messages never come back as observations.
 */
class JulLogObserver(
    private val platformLogger: () -> Logger,
    private val fallbackOwner: Any,
    private val resolveOwner: (loggerName: String?) -> Any?,
) {
    private val ownLogCall = ThreadLocal.withInitial { false }

    @Volatile
    private var observer: ((Any, LogLevel, String, Throwable?) -> Unit)? = null
    private var attachedTo: Logger? = null

    private val handler = object : Handler() {
        override fun publish(record: LogRecord?) {
            if (record == null || ownLogCall.get()) return
            if (record.level.intValue() < Level.WARNING.intValue()) return
            val level = if (record.level.intValue() >= Level.SEVERE.intValue()) LogLevel.ERROR else LogLevel.WARNING
            val owner = resolveOwner(record.loggerName) ?: fallbackOwner
            observer?.invoke(owner, level, record.message ?: "Native platform error", record.thrown)
        }

        override fun flush() = Unit
        override fun close() = Unit
    }

    /** Starts forwarding to [value], or stops when it is null. Repeated calls only swap the callback. */
    @Synchronized
    fun observe(value: ((Any, LogLevel, String, Throwable?) -> Unit)?) {
        observer = value
        if (value != null && attachedTo == null) {
            attachedTo = platformLogger().also { it.addHandler(handler) }
        } else if (value == null) {
            attachedTo?.removeHandler(handler)
            attachedTo = null
        }
    }

    /** Runs [block] while records produced by this thread are treated as the library's own. */
    fun <T> runOwn(block: () -> T): T {
        val previous = ownLogCall.get()
        ownLogCall.set(true)
        try {
            return block()
        } finally {
            ownLogCall.set(previous)
        }
    }

    /**
     * Platform log handler that writes each record to the logger [loggerFor] picks for its owner, at the
     * matching [julLevel], without the record coming back as an observation.
     */
    fun logHandler(loggerFor: (owner: Any) -> Logger): (Any, LogLevel, String, Throwable?) -> Unit =
        { owner, level, message, error ->
            runOwn { loggerFor(owner).log(julLevel(level), message, error) }
        }

    companion object {
        /** The `java.util.logging` level a record of [level] is written at; errors are SEVERE. */
        @JvmStatic
        fun julLevel(level: LogLevel): Level = when (level) {
            LogLevel.WARNING -> Level.WARNING
            LogLevel.ERROR -> Level.SEVERE
            else -> Level.INFO
        }

        /** The plugin among [plugins] whose [name] identifies [loggerName], chosen by [matchName]. */
        @JvmStatic
        fun <P : Any> matchPlugin(loggerName: String?, plugins: Collection<P>, name: (P) -> String): P? =
            matchName(loggerName, plugins.map(name))?.let { match -> plugins.first { name(it) == match } }

        /**
         * Picks the name that identifies [loggerName]: an exact case-insensitive match first, otherwise
         * the longest name of at least three characters contained in it.
         */
        @JvmStatic
        fun matchName(loggerName: String?, names: Collection<String>): String? {
            if (loggerName.isNullOrBlank()) return null
            names.firstOrNull { it.equals(loggerName, ignoreCase = true) }?.let { return it }
            return names.filter { it.length >= MIN_PARTIAL_LENGTH && loggerName.contains(it, ignoreCase = true) }
                .maxByOrNull { it.length }
        }

        private const val MIN_PARTIAL_LENGTH = 3
    }
}
