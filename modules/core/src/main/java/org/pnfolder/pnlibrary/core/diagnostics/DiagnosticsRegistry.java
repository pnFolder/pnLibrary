package org.pnfolder.pnlibrary.core.diagnostics;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticRegistration;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsContributor;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticLevel;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.DiagnosticValueSanitizer;

/**
 * Per-plugin diagnostic state behind {@link DiagnosticsService}.
 *
 * <p>Converts throwables immediately so plugin exceptions are never retained beyond the call frame. All stored
 * strings are bounded and redacted by {@link DiagnosticValueSanitizer}.
 *
 * <p>Contributor registration and status lookup are concurrent. Event deques are synchronized per plugin because
 * deduplication and timeline updates must be atomic relative to snapshots.
 */
public final class DiagnosticsRegistry implements DiagnosticsService {
    private static final int DEFAULT_EVENT_LIMIT = 100;

    private final ConcurrentHashMap<String, PluginState> plugins = new ConcurrentHashMap<String, PluginState>();
    private final int eventLimit;
    private final DiagnosticValueSanitizer sanitizer = new DiagnosticValueSanitizer();
    private volatile Runnable eventChangeListener;
    private volatile DiagnosticEventListener activityListener;
    private volatile BiConsumer<String, Throwable> internalFailure = (what, error) -> {
        };

    /** Creates a registry that keeps 100 distinct incidents per plugin. */
    public DiagnosticsRegistry() {
        this(DEFAULT_EVENT_LIMIT);
    }

    /**
     * Creates a registry that keeps {@code eventLimit} distinct incidents per plugin; values outside
     * {@code 10..500} are clamped to that range.
     */
    public DiagnosticsRegistry(int eventLimit) {
        this.eventLimit = Math.max(10, Math.min(eventLimit, 500));
    }

    /**
     * Sets who is told what failed when a listener or a plugin's contributor throws. None of them may reject a
     * diagnostic event, so the failure ends here instead of with the caller.
     */
    public void onInternalFailure(@NotNull BiConsumer<String, Throwable> listener) {
        internalFailure = listener;
    }

    /**
     * Installs a best-effort callback invoked after an event changes. The runtime uses it to persist diagnostic
     * history; failures of the callback are isolated from event recording. {@code null} removes the hook.
     */
    public void onEventsChanged(@Nullable Runnable listener) {
        eventChangeListener = listener;
    }

    /** Installs the observer that sees every recorded event after redaction; {@code null} removes it. */
    public void onActivityEvent(@Nullable DiagnosticEventListener listener) {
        activityListener = listener;
    }

    @NotNull
    @Override
    public DiagnosticRegistration register(@NotNull String plugin, @NotNull DiagnosticsContributor contributor) {
        return stateOf(plugin).contributors.register(contributor, null);
    }

    @NotNull
    @Override
    public DiagnosticRegistration register(@NotNull String plugin, @NotNull Path dataDirectory,
                                           @NotNull DiagnosticsContributor contributor) {
        return stateOf(plugin).contributors.register(contributor, dataDirectory.toAbsolutePath().normalize());
    }

    @Override
    public void status(@NotNull String plugin, @NotNull String component, @NotNull String state,
                       @NotNull String detail, @NotNull Map<String, Object> fields) {
        stateOf(plugin).statuses.update(component, state, detail, fields);
    }

    @Override
    public void clearStatus(@NotNull String plugin, @NotNull String component) {
        PluginState state = plugins.get(sanitizer.key(plugin));
        if (state != null) {
            state.statuses.remove(component);
        }
    }

    @Override
    public void clearPlugin(@NotNull String plugin) {
        plugins.remove(sanitizer.key(plugin));
    }

    /** Drops every plugin and both listeners, so a reloaded runtime starts from nothing. */
    public void clear() {
        plugins.clear();
        eventChangeListener = null;
        activityListener = null;
    }

    @Override
    public void record(@NotNull String plugin, @NotNull DiagnosticLevel level, @NotNull String component,
                       @NotNull String code, @NotNull String message, @Nullable Throwable error,
                       @NotNull Map<String, Object> fields) {
        String safeComponent = sanitizer.text(component, 96);
        String safeCode = sanitizer.text(code, 96);
        String safeMessage = sanitizer.text(message, 4096);
        Map<String, Object> safeFields = sanitizer.map(fields);
        notifyActivityListener(plugin, level, safeComponent, safeCode, safeMessage, error, safeFields);
        stateOf(plugin).incidents.record(plugin, level, safeComponent, safeCode, safeMessage, error, safeFields);
        notifyEventsChanged();
    }

    /**
     * Builds a snapshot of all (or one) plugin's diagnostic state; contributor errors are captured per container
     * so they do not break the entire report.
     */
    @NotNull
    public Map<String, Object> snapshot(@Nullable String selectedPlugin) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (String name : sortedNames()) {
            PluginState state = plugins.get(name);
            if (state == null || !selected(name, selectedPlugin)) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("statuses", state.statuses.snapshot());
            entry.put("events", state.incidents.snapshot());
            entry.put("contributors", state.contributors.collect());
            result.put(name, entry);
        }
        return result;
    }

    /** Persistent-history view without invoking plugin contributors. */
    @NotNull
    public Map<String, Object> eventSnapshot() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : incidentsByPlugin().entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /** The configuration files declared by {@code plugin} and its modules, or by everyone for {@code all}. */
    @NotNull
    public List<RegisteredDiagnosticConfiguration> configurations(@NotNull String plugin) {
        List<RegisteredDiagnosticConfiguration> result = new ArrayList<RegisteredDiagnosticConfiguration>();
        for (String name : sortedNames()) {
            PluginState state = plugins.get(name);
            if (state != null && selected(name, plugin)) {
                result.addAll(state.contributors.configurations(name));
            }
        }
        return result;
    }

    /** Returns bounded incident history analytics without invoking contributors. */
    @NotNull
    public Map<String, Object> historyAnalytics() {
        return historyAnalytics(DiagnosticHistoryAnalytics.DEFAULT_LIMIT);
    }

    /** Returns bounded incident history analytics listing at most {@code limit} recent incidents. */
    @NotNull
    public Map<String, Object> historyAnalytics(int limit) {
        return DiagnosticHistoryAnalytics.analyze(incidentsByPlugin(), limit);
    }

    /** Lightweight aggregate counters used by runtime support reports. */
    @NotNull
    public Map<String, Object> diagnosticSummary() {
        List<Object> summaries = new ArrayList<Object>();
        for (String name : sortedNames()) {
            PluginState state = plugins.get(name);
            if (state != null) {
                summaries.add(pluginSummary(name, state));
            }
        }
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("registeredPlugins", plugins.size());
        summary.put("plugins", summaries);
        return summary;
    }

    private Map<String, Object> pluginSummary(String name, PluginState state) {
        Map<String, Map<String, Object>> statuses = state.statuses.snapshot();
        Map<String, Integer> states = new LinkedHashMap<String, Integer>();
        for (Map<String, Object> status : statuses.values()) {
            Object value = status.get("state");
            if (value != null) {
                String key = value.toString();
                Integer previous = states.get(key);
                states.put(key, previous == null ? 1 : previous + 1);
            }
        }
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("plugin", name);
        summary.put("statusCount", statuses.size());
        summary.put("statusStates", states);
        summary.put("contributorCount", state.contributors.size());
        summary.put("incidents", state.incidents.summary());
        return summary;
    }

    private Map<String, List<Map<String, Object>>> incidentsByPlugin() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String name : sortedNames()) {
            PluginState state = plugins.get(name);
            if (state == null) {
                continue;
            }
            List<Map<String, Object>> events = state.incidents.snapshot();
            if (!events.isEmpty()) {
                result.put(name, events);
            }
        }
        return result;
    }

    private List<String> sortedNames() {
        List<String> names = new ArrayList<String>(plugins.keySet());
        Collections.sort(names);
        return names;
    }

    private boolean selected(String name, String selectedPlugin) {
        if (selectedPlugin == null || "all".equalsIgnoreCase(selectedPlugin)) {
            return true;
        }
        String key = sanitizer.key(selectedPlugin);
        return name.equals(key) || name.startsWith(key + "/");
    }

    private PluginState stateOf(String plugin) {
        return plugins.computeIfAbsent(sanitizer.key(plugin), key -> new PluginState(eventLimit, sanitizer,
            (what, error) -> internalFailure.accept(what, error)));
    }

    private void notifyActivityListener(String plugin, DiagnosticLevel level, String component, String code,
                                        String message, Throwable error, Map<String, Object> fields) {
        DiagnosticEventListener listener = activityListener;
        if (listener == null) {
            return;
        }
        try {
            listener.onEvent(plugin, level, component, code, message, error, fields);
        } catch (Exception failure) {
            // Compatibility listeners are observers and must never reject diagnostics.
            internalFailure.accept("The diagnostic activity listener failed", failure);
        }
    }

    private void notifyEventsChanged() {
        Runnable listener = eventChangeListener;
        if (listener == null) {
            return;
        }
        try {
            listener.run();
        } catch (Exception failure) {
            // Persistence callbacks are best-effort and must not reject the event.
            internalFailure.accept("The diagnostic history callback failed", failure);
        }
    }

    private static final class PluginState {
        final DiagnosticContributors contributors;
        final DiagnosticStatusStore statuses;
        final DiagnosticIncidentLog incidents;

        PluginState(int eventLimit, DiagnosticValueSanitizer sanitizer, BiConsumer<String, Throwable> onFailure) {
            this.contributors = new DiagnosticContributors(sanitizer, onFailure);
            this.statuses = new DiagnosticStatusStore(sanitizer);
            this.incidents = new DiagnosticIncidentLog(eventLimit, sanitizer);
        }
    }
}
