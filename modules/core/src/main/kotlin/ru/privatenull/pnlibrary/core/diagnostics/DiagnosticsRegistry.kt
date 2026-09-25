package ru.privatenull.pnlibrary.core.diagnostics

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticConfiguration
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticLevel
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsContributor
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsService
import java.nio.file.Path
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe implementation of [DiagnosticsService].
 *
 * Converts throwables immediately so plugin exceptions are never retained
 * beyond the call frame. All stored strings are bounded and redacted by
 * [DiagnosticValueSanitizer].
 *
 * Contributor registration and status lookup are concurrent. Event deques are
 * synchronized per plugin because deduplication and timeline updates must be
 * atomic relative to snapshots.
 *
 * @param eventLimit maximum number of distinct incidents retained per plugin;
 * values outside `10..500` are clamped to that range
 */
internal class DiagnosticsRegistry(eventLimit: Int = DEFAULT_EVENT_LIMIT) : DiagnosticsService {

    private val plugins = ConcurrentHashMap<String, PluginState>()
    private val limit   = eventLimit.coerceIn(10, 500)
    private val sanitizer = DiagnosticValueSanitizer()
    @Volatile private var eventChangeListener: (() -> Unit)? = null

    /**
     * Installs a best-effort callback invoked after an event changes.
     *
     * The runtime uses this hook to persist diagnostic history. Callback failures
     * are isolated from event recording. Passing `null` removes the hook.
     */
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
        val id = sanitizer.text(contributor.id, 96).also {
            require(it.isNotEmpty()) { "contributor id must not be empty" }
        }
        val registered = RegisteredContributor(contributor, dataDirectory)
        state.contributors[id] = registered
        return object : DiagnosticRegistration {
            private val closed = java.util.concurrent.atomic.AtomicBoolean(false)
            override val isClosed: Boolean get() = closed.get()
            override fun close() {
                if (closed.compareAndSet(false, true)) {
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
        stateOf(plugin).statuses[sanitizer.key(component)] = linkedMapOf(
            "state"      to sanitizer.text(state, 64),
            "detail"     to sanitizer.text(detail, 2048),
            "updatedUtc" to Instant.now().toString(),
            "fields"     to sanitizer.map(fields),
        )
    }

    override fun clearStatus(plugin: String, component: String) {
        plugins[sanitizer.key(plugin)]?.statuses?.remove(sanitizer.key(component))
    }

    override fun clearPlugin(plugin: String) {
        plugins.remove(sanitizer.key(plugin))
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
        val safeComponent = sanitizer.text(component, 96)
        val safeCode = sanitizer.text(code, 96)
        val safeMessage = sanitizer.text(message, 4096)
        val safeFields = sanitizer.map(fields)
        val incidentId = sanitizer.incidentId(plugin, level, safeComponent, safeCode, safeMessage, error)
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
                notifyEventsChanged()
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
                "origin" to error?.let(sanitizer::exceptionOrigin),
                "occurrenceTimeline" to mutableListOf(occurrence(now, safeFields)),
            )
            if (error != null) event["exception"] = sanitizer.exception(error)
            st.events.addLast(event)
            while (st.events.size > limit) st.events.removeFirst()
            notifyEventsChanged()
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
            if (!all && name != sanitizer.key(selectedPlugin)) return@forEach

            val contributions = linkedMapOf<String, Any?>()
            st.contributors.forEach { (cId, registered) ->
                contributions[cId] = try {
                    sanitizer.map(registered.contributor.collect())
                } catch (exception: Exception) {
                    mapOf("collectionError" to sanitizer.exception(exception))
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
        val pluginKey = sanitizer.key(plugin)
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
                        runCatching { files[path] = RegisteredConfiguration(plugin, registered.dataDirectory, DiagnosticConfiguration.builder(path).build()) }
                    }
                }
            } catch (_: Exception) {
                // A malformed contributor must not prevent other configurations.
            }
        }
        return files.values.toList()
    }

    /**
     * Configuration declaration resolved against the owning plugin directory.
     *
     * @property plugin normalized plugin identifier used in report paths
     * @property dataDirectory trusted plugin root, or `null` for the runtime root
     * @property configuration collection and redaction policy supplied by the plugin
     */
    data class RegisteredConfiguration(
        val plugin: String,
        val dataDirectory: Path?,
        val configuration: DiagnosticConfiguration,
    )

    private fun stateOf(plugin: String) =
        plugins.computeIfAbsent(sanitizer.key(plugin)) { PluginState() }

    private fun notifyEventsChanged() {
        try {
            eventChangeListener?.invoke()
        } catch (_: Exception) {
            // Persistence callbacks are best-effort and must not reject the event.
        }
    }

    private fun occurrence(timeUtc: String, fields: Map<String, Any?>): Map<String, Any?> = linkedMapOf(
        "timeUtc" to timeUtc,
        "thread" to Thread.currentThread().name,
        "fields" to fields,
    )

    private class PluginState {
        val contributors = ConcurrentHashMap<String, RegisteredContributor>()
        val statuses     = ConcurrentHashMap<String, Map<String, Any?>>()
        val events       = ArrayDeque<LinkedHashMap<String, Any?>>()
    }
    private data class RegisteredContributor(val contributor: DiagnosticsContributor, val dataDirectory: Path?)

    private companion object {
        const val DEFAULT_EVENT_LIMIT = 100
        const val MAX_EVENT_TIMELINE = 100_000
    }
}
