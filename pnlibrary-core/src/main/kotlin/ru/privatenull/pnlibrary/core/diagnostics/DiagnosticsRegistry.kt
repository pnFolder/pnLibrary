package ru.privatenull.pnlibrary.core.diagnostics

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticConfiguration
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticLevel
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsContributor
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsService
import java.time.Instant
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.nio.file.Path

/**
 * Thread-safe implementation of [DiagnosticsService].
 *
 * Converts throwables immediately so plugin exceptions are never retained
 * beyond the call frame.  All stored strings are bounded and redacted.
 */
class DiagnosticsRegistry(eventLimit: Int = DEFAULT_EVENT_LIMIT) : DiagnosticsService {

    private val plugins = ConcurrentHashMap<String, PluginState>()
    private val limit   = eventLimit.coerceIn(10, 500)
    private val redactor = DiagnosticRedactor()
    @Volatile private var eventChangeListener: (() -> Unit)? = null

    fun onEventsChanged(listener: (() -> Unit)?) {
        eventChangeListener = listener
    }

    override val apiVersion: Int get() = DiagnosticsService.API_VERSION

    // ── Registration ─────────────────────────────────────────────────────────

    override fun register(plugin: String, contributor: DiagnosticsContributor): DiagnosticRegistration {
        return registerInternal(plugin, null, contributor)
    }

    override fun register(plugin: String, dataDirectory: Path, contributor: DiagnosticsContributor): DiagnosticRegistration {
        return registerInternal(plugin, dataDirectory.toAbsolutePath().normalize(), contributor)
    }

    private fun registerInternal(plugin: String, dataDirectory: Path?, contributor: DiagnosticsContributor): DiagnosticRegistration {
        val state = stateOf(plugin)
        val id    = bounded(contributor.id, 96).also {
            require(it.isNotEmpty()) { "contributor id must not be empty" }
        }
        val registered = RegisteredContributor(contributor, dataDirectory)
        state.contributors[id] = registered
        var closed = false
        return DiagnosticRegistration {
            synchronized(this) {
                if (!closed) {
                    closed = true
                    state.contributors.remove(id, registered)
                }
            }
        }
    }

    // ── Status ───────────────────────────────────────────────────────────────

    override fun status(
        plugin: String, component: String,
        state: String, detail: String, fields: Map<String, Any?>,
    ) {
        stateOf(plugin).statuses[key(component)] = linkedMapOf(
            "state"      to bounded(state, 64),
            "detail"     to bounded(detail, 2048),
            "updatedUtc" to Instant.now().toString(),
            "fields"     to safeMap(fields),
        )
    }

    override fun clearStatus(plugin: String, component: String) {
        plugins[key(plugin)]?.statuses?.remove(key(component))
    }

    override fun clearPlugin(plugin: String) {
        plugins.remove(key(plugin))
    }

    /** Clears process state when the installed runtime is shut down or reloaded. */
    fun clear() {
        plugins.clear()
        eventChangeListener = null
    }

    // ── Event recording ──────────────────────────────────────────────────────

    override fun record(
        plugin: String, level: DiagnosticLevel,
        component: String, code: String, message: String,
        error: Throwable?, fields: Map<String, Any?>,
    ) {
        val now = Instant.now().toString()
        val safeComponent = bounded(component, 96)
        val safeCode = bounded(code, 96)
        val safeMessage = bounded(message, 4096)
        val safeFields = safeMap(fields)
        val incidentId = incidentId(plugin, level, safeComponent, safeCode, safeMessage, error)
        val st = stateOf(plugin)
        synchronized(st.events) {
            val existing = st.events.firstOrNull { it["incidentId"] == incidentId }
            if (existing != null) {
                val count = (existing["occurrenceCount"] as? Number)?.toLong() ?: 1L
                existing["occurrenceCount"] = count + 1
                existing["lastSeenUtc"] = now
                @Suppress("UNCHECKED_CAST")
                val timeline = existing["occurrenceTimeline"] as MutableList<Map<String, Any?>>
                if (timeline.size < MAX_EVENT_TIMELINE) {
                    timeline += occurrence(now, safeFields)
                } else {
                    existing["omittedOccurrences"] =
                        ((existing["omittedOccurrences"] as? Number)?.toLong() ?: 0L) + 1
                }
                eventChangeListener?.let { runCatching(it) }
                return
            }

            val event = linkedMapOf<String, Any?>(
                "incidentId" to incidentId,
                "timeUtc" to now,
                "firstSeenUtc" to now,
                "lastSeenUtc" to now,
                "occurrenceCount" to 1L,
                "omittedOccurrences" to 0L,
                "level" to level.name,
                "component" to safeComponent,
                "code" to safeCode,
                "message" to safeMessage,
                "fields" to safeFields,
                "origin" to error?.let(::exceptionOrigin),
                "occurrenceTimeline" to mutableListOf(occurrence(now, safeFields)),
            )
            if (error != null) event["exception"] = formatException(error)
            st.events.addLast(event)
            while (st.events.size > limit) st.events.removeFirst()
            eventChangeListener?.let { runCatching(it) }
        }
    }

    // ── Snapshot ─────────────────────────────────────────────────────────────

    /**
     * Builds a snapshot of all (or one) plugin's diagnostic state.
     *
     * Contributor errors are captured per-container so they do not break the
     * entire report.
     */
    fun snapshot(selectedPlugin: String?): Map<String, Any?> {
        val all = selectedPlugin == null || selectedPlugin.equals("all", ignoreCase = true)
        val result = linkedMapOf<String, Any?>()
        plugins.keys.sorted().forEach { name ->
            val st = plugins[name] ?: return@forEach
            if (!all && name != key(selectedPlugin ?: "")) return@forEach

            val contributions = linkedMapOf<String, Any?>()
            st.contributors.forEach { (cId, registered) ->
                contributions[cId] = try {
                    safeMap(registered.contributor.collect())
                } catch (t: Throwable) {
                    mapOf("collectionError" to formatException(t))
                }
            }

            result[name] = linkedMapOf<String, Any?>(
                "statuses"     to LinkedHashMap(st.statuses),
                "events"       to synchronized(st.events) { ArrayList(st.events) },
                "contributors" to contributions,
            )
        }
        return result
    }

    /** Persistent-history view without invoking plugin contributors. */
    fun eventSnapshot(): Map<String, Any?> = linkedMapOf<String, Any?>().also { result ->
        plugins.keys.sorted().forEach { name ->
            val state = plugins[name] ?: return@forEach
            val events = synchronized(state.events) { ArrayList(state.events) }
            if (events.isNotEmpty()) result[name] = events
        }
    }

    /** Returns merged [DiagnosticConfiguration] objects for a given plugin. */
    fun configurations(plugin: String): List<RegisteredConfiguration> {
        if (plugin.equals("all", ignoreCase = true)) {
            return plugins.keys.sorted().flatMap { pluginKey -> configurationsFor(pluginKey, plugins[pluginKey] ?: return@flatMap emptyList()) }
        }
        val pluginKey = key(plugin)
        val st = plugins[pluginKey] ?: return emptyList()
        return configurationsFor(pluginKey, st)
    }

    private fun configurationsFor(plugin: String, st: PluginState): List<RegisteredConfiguration> {
        val files = linkedMapOf<String, RegisteredConfiguration>()
        st.contributors.values.forEach { registered ->
            try {
                val contrib = registered.contributor
                contrib.configurations().forEach { cfg ->
                    if (files.size < 64) files.putIfAbsent(cfg.path, RegisteredConfiguration(plugin, registered.dataDirectory, cfg))
                }
                contrib.configurationFiles().forEach { path ->
                    if (files.size < 64 && !files.containsKey(path)) {
                        runCatching { files[path] = RegisteredConfiguration(plugin, registered.dataDirectory, DiagnosticConfiguration.file(path).build()) }
                    }
                }
            } catch (_: Throwable) { /* never let a contributor break the report */ }
        }
        return files.values.toList()
    }

    data class RegisteredConfiguration(
        val plugin: String,
        val dataDirectory: Path?,
        val configuration: DiagnosticConfiguration,
    )

    // ── Internals ────────────────────────────────────────────────────────────

    private fun stateOf(plugin: String) =
        plugins.computeIfAbsent(key(plugin)) { PluginState() }

    private fun key(value: String?): String {
        val k = value?.trim()?.lowercase(Locale.ROOT) ?: "unknown"
        return if (k.isEmpty()) "unknown" else k.take(96)
    }

    private fun bounded(value: String?, max: Int): String {
        val safe = redactor.redact(value ?: "")
        return safe.take(max)
    }

    private fun safeMap(values: Map<*, *>?): Map<String, Any?> =
        safeMapInner(values, 0, IdentityHashMapWrapper())

    @Suppress("UNCHECKED_CAST")
    private fun safeMapInner(
        values: Map<*, *>?,
        depth: Int,
        seen: IdentityHashMapWrapper,
    ): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        if (values == null) return result
        if (depth >= 8 || !seen.add(values)) {
            result["limit"] = "[recursive/depth limit]"
            return result
        }
        var count = 0
        for ((rawKey, rawVal) in values) {
            if (count++ >= 128) break
            val k = bounded(rawKey?.toString() ?: "", 128)
            result[k] = if (isSecretKey(k)) "[REDACTED]"
                        else safeValue(rawVal, depth + 1, seen)
        }
        seen.remove(values)
        return result
    }

    private fun safeValue(value: Any?, depth: Int, seen: IdentityHashMapWrapper): Any? {
        if (value == null || value is Number || value is Boolean) return value
        if (depth >= 8) return "[depth limit]"
        if (value is Map<*, *>) return safeMapInner(value, depth, seen)
        if (value is Iterable<*>) {
            if (!seen.add(value)) return "[recursive reference]"
            val list = mutableListOf<Any?>()
            for (item in value) {
                if (list.size >= 128) break
                list.add(safeValue(item, depth + 1, seen))
            }
            seen.remove(value)
            return list
        }
        return bounded(value.toString(), 4096)
    }

    private fun formatException(error: Throwable): String {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        val complete = redactor.redact(writer.toString())
        if (complete.length <= MAX_EXCEPTION_CHARS) return complete
        return complete.take(MAX_EXCEPTION_CHARS) +
            "\n[TRUNCATED: throwable exceeded the hard $MAX_EXCEPTION_CHARS-character safety limit]"
    }

    private fun occurrence(timeUtc: String, fields: Map<String, Any?>): Map<String, Any?> = linkedMapOf(
        "timeUtc" to timeUtc,
        "thread" to Thread.currentThread().name,
        "fields" to fields,
    )

    private fun incidentId(
        plugin: String,
        level: DiagnosticLevel,
        component: String,
        code: String,
        message: String,
        error: Throwable?,
    ): String {
        val significant = unwrap(error)
        val frame = significant?.stackTrace?.firstOrNull(::isApplicationFrame)
            ?: significant?.stackTrace?.firstOrNull()
        val source = listOf(
            key(plugin), level.name, component, code, normalize(message),
            significant?.javaClass?.name.orEmpty(), normalize(significant?.message.orEmpty()),
            frame?.className.orEmpty(), frame?.methodName.orEmpty(),
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
    }

    private fun exceptionOrigin(error: Throwable): Map<String, Any?>? {
        val significant = unwrap(error) ?: error
        val frame = significant.stackTrace.firstOrNull(::isApplicationFrame)
            ?: significant.stackTrace.firstOrNull()
            ?: return null
        return linkedMapOf(
            "class" to frame.className,
            "method" to frame.methodName,
            "file" to frame.fileName,
            "line" to frame.lineNumber.takeIf { it >= 0 },
        )
    }

    private fun unwrap(error: Throwable?): Throwable? {
        var current = error ?: return null
        while ((current is java.util.concurrent.CompletionException ||
                current is java.util.concurrent.ExecutionException) && current.cause != null) {
            current = current.cause!!
        }
        return current
    }

    private fun isApplicationFrame(frame: StackTraceElement): Boolean = IGNORED_FRAME_PREFIXES.none {
        frame.className.startsWith(it)
    }

    private fun normalize(value: String): String = value
        .replace(UUID_PATTERN, "<uuid>")
        .replace(LONG_NUMBER_PATTERN, "<number>")
        .replace(WHITESPACE_PATTERN, " ")
        .trim()

    private fun isSecretKey(key: String): Boolean =
        key.matches(Regex("(?i).*(?:password|passwd|pwd|secret|token|api[-_ ]?key|authorization|cookie|private[-_ ]?key|credential).*"))

    /** Wraps java.util.IdentityHashMap to avoid unchecked-cast warnings. */
    private class IdentityHashMapWrapper {
        private val map = java.util.IdentityHashMap<Any, Boolean>()
        fun add(o: Any): Boolean = map.put(o, true) == null
        fun remove(o: Any) { map.remove(o) }
    }

    private class PluginState {
        val contributors = ConcurrentHashMap<String, RegisteredContributor>()
        val statuses     = ConcurrentHashMap<String, Map<String, Any?>>()
        val events       = ArrayDeque<LinkedHashMap<String, Any?>>()
    }
    private data class RegisteredContributor(val contributor: DiagnosticsContributor, val dataDirectory: Path?)

    private companion object {
        const val DEFAULT_EVENT_LIMIT = 100
        const val MAX_EXCEPTION_CHARS = 1_048_576
        const val MAX_EVENT_TIMELINE = 100_000
        val UUID_PATTERN = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b")
        val LONG_NUMBER_PATTERN = Regex("\\b\\d{4,}\\b")
        val WHITESPACE_PATTERN = Regex("\\s+")
        val IGNORED_FRAME_PREFIXES = listOf(
            "java.", "javax.", "kotlin.", "kotlinx.", "sun.", "jdk.",
            "org.bukkit.", "net.minecraft.", "io.papermc.", "com.destroystokyo.paper.",
            "ru.privatenull.pnlibrary.",
        )
    }
}
