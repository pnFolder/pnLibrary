package ru.privatenull.pnlibrary.core.diagnostics

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticLevel
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsContributor
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsService
import java.nio.file.Path
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
    @Volatile
    private var eventChangeListener: (() -> Unit)? = null

    @Volatile
    private var activityListener: (
        (
            plugin: String,
            level: DiagnosticLevel,
            category: String,
            code: String,
            message: String,
            error: Throwable?,
            context: Map<String, Any?>,
        ) -> Unit
    )? = null

    /**
     * Installs a best-effort callback invoked after an event changes.
     *
     * The runtime uses this hook to persist diagnostic history. Callback failures
     * are isolated from event recording. Passing `null` removes the hook.
     */
    fun onEventsChanged(listener: (() -> Unit)?) {
        eventChangeListener = listener
    }

    fun onActivityEvent(listener: ((String, DiagnosticLevel, String, String, String, Throwable?, Map<String, Any?>) -> Unit)?) {
        activityListener = listener
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
        return stateOf(plugin).contributors.register(contributor, dataDirectory)
    }

    // ── Status ───────────────────────────────────────────────────────────────

    override fun status(
        plugin: String, component: String,
        state: String, detail: String, fields: Map<String, Any?>,
    ) {
        stateOf(plugin).statuses.update(component, state, detail, fields)
    }

    override fun clearStatus(plugin: String, component: String) {
        plugins[sanitizer.key(plugin)]?.statuses?.remove(component)
    }

    override fun clearPlugin(plugin: String) {
        plugins.remove(sanitizer.key(plugin))
    }

    /** Clears process state when the installed runtime is shut down or reloaded. */
    fun clear() {
        plugins.clear()
        eventChangeListener = null
        activityListener = null
    }

    // ── Event recording ──────────────────────────────────────────────────────

    override fun record(
        plugin: String, level: DiagnosticLevel,
        component: String, code: String, message: String,
        error: Throwable?, fields: Map<String, Any?>,
    ) {
        val safeComponent = sanitizer.text(component, 96)
        val safeCode = sanitizer.text(code, 96)
        val safeMessage = sanitizer.text(message, 4096)
        val safeFields = sanitizer.map(fields)
        notifyActivityListener(plugin, level, safeComponent, safeCode, safeMessage, error, safeFields)

        stateOf(plugin).incidents.record(
            plugin, level, safeComponent, safeCode, safeMessage, error, safeFields,
        )
        notifyEventsChanged()
    }

    private fun notifyActivityListener(
        plugin: String,
        level: DiagnosticLevel,
        component: String,
        code: String,
        message: String,
        error: Throwable?,
        fields: Map<String, Any?>,
    ) {
        try {
            activityListener?.invoke(plugin, level, component, code, message, error, fields)
        } catch (_: Exception) {
            // Compatibility listeners are observers and must never reject diagnostics.
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

            result[name] = linkedMapOf<String, Any?>(
                "statuses"     to st.statuses.snapshot(),
                "events"       to st.incidents.snapshot(),
                "contributors" to st.contributors.collect(),
            )
        }
        return result
    }

    /** Persistent-history view without invoking plugin contributors. */
    fun eventSnapshot(): Map<String, Any?> = linkedMapOf<String, Any?>().also { result ->
        plugins.keys.sorted().forEach { name ->
            val state = plugins[name] ?: return@forEach
            val events = state.incidents.snapshot()
            if (events.isNotEmpty()) result[name] = events
        }
    }

    /** Returns merged configuration declarations for a given plugin. */
    fun configurations(plugin: String): List<RegisteredDiagnosticConfiguration> {
        if (plugin.equals("all", ignoreCase = true)) {
            return plugins.keys.sorted().flatMap { pluginKey ->
                plugins[pluginKey]?.contributors?.configurations(pluginKey).orEmpty()
            }
        }
        val pluginKey = sanitizer.key(plugin)
        return plugins[pluginKey]?.contributors?.configurations(pluginKey).orEmpty()
    }

    /** Lightweight aggregate counters used by runtime support reports. */
    fun diagnosticSummary(): Map<String, Any?> = linkedMapOf<String, Any?>(
        "registeredPlugins" to plugins.size,
        "plugins" to plugins.keys.sorted().mapNotNull { name ->
            plugins[name]?.let { state ->
                val statuses = state.statuses.snapshot()
                linkedMapOf<String, Any?>(
                    "plugin" to name,
                    "statusCount" to statuses.size,
                    "statusStates" to statuses.values
                        .mapNotNull { it["state"]?.toString() }
                        .groupingBy { it }
                        .eachCount(),
                    "contributorCount" to state.contributors.size(),
                    "incidents" to state.incidents.summary(),
                )
            }
        },
    )

    private fun stateOf(plugin: String) =
        plugins.computeIfAbsent(sanitizer.key(plugin)) { PluginState(limit, sanitizer) }

    private fun notifyEventsChanged() {
        try {
            eventChangeListener?.invoke()
        } catch (_: Exception) {
            // Persistence callbacks are best-effort and must not reject the event.
        }
    }

    private class PluginState(eventLimit: Int, sanitizer: DiagnosticValueSanitizer) {
        val contributors = DiagnosticContributors(sanitizer)
        val statuses = DiagnosticStatusStore(sanitizer)
        val incidents = DiagnosticIncidentLog(eventLimit, sanitizer)
    }

    private companion object {
        const val DEFAULT_EVENT_LIMIT = 100
    }
}
