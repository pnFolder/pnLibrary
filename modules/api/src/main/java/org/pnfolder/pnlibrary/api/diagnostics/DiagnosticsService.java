package org.pnfolder.pnlibrary.api.diagnostics;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticLevel;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/**
 * Public diagnostic service exposed through {@link org.pnfolder.pnlibrary.api.runtime.PnLibrary}.
 *
 * <pre>{@code
 * DiagnosticsService service = PnLibraryProvider.get().getDiagnostics();
 * DiagnosticRegistration registration = service.register("pnMarket",
 *     DiagnosticContainer.builder("auction").snapshot(() -> snapshot()).build());
 * }</pre>
 *
 * <p>Registration, status updates, clearing, and event recording are safe from arbitrary threads. Contributor
 * callbacks may be collected concurrently and therefore must be thread-safe and reasonably fast; slow file/network work
 * does not belong in a diagnostic contributor.
 */
@ApiStatus.NonExtendable
public interface DiagnosticsService {
    /** Current public diagnostics contract version. */
    int API_VERSION = 2;

    /** Incremented when the API contract changes incompatibly. */
    default int getApiVersion() {
        return API_VERSION;
    }

    /**
     * Registers a {@link DiagnosticsContributor} (or {@code DiagnosticContainer}) under the given plugin name.
     *
     * @param plugin logical plugin name, case-insensitive (e.g. {@code "pnMarket"})
     * @param contributor the contributor supplying diagnostic snapshots
     * @return a {@link DiagnosticRegistration} that removes the contributor when closed
     */
    @NotNull
    DiagnosticRegistration register(@NotNull String plugin, @NotNull DiagnosticsContributor contributor);

    /**
     * Registers {@code contributor} and associates it with the plugin data directory {@code dataDirectory}.
     *
     * <p>The library uses {@code dataDirectory} to locate plugin-owned files that belong in diagnostic reports.
     */
    @NotNull
    DiagnosticRegistration register(@NotNull String plugin, @NotNull Path dataDirectory, @NotNull DiagnosticsContributor contributor);

    /**
     * Updates or creates a component status entry visible in diagnostic reports.
     *
     * @param plugin owning plugin name
     * @param component component identifier, e.g. {@code "database"}
     * @param state short status string, e.g. {@code "CONNECTED"} or {@code "DEGRADED"}
     * @param detail optional human-readable detail; may be empty
     * @param fields arbitrary structured fields attached to the status entry
     */
    void status(@NotNull String plugin, @NotNull String component, @NotNull String state, @NotNull String detail,
                @NotNull Map<String, Object> fields);

    /** Like the full call, with no detail and no fields. */
    default void status(@NotNull String plugin, @NotNull String component, @NotNull String state) {
        status(plugin, component, state, "", Collections.emptyMap());
    }

    /** Like the full call, with no fields. */
    default void status(@NotNull String plugin, @NotNull String component, @NotNull String state, @NotNull String detail) {
        status(plugin, component, state, detail, Collections.emptyMap());
    }

    /** Removes the component status entry previously set by {@link #status}. */
    void clearStatus(@NotNull String plugin, @NotNull String component);

    /** Removes every contributor, status and event owned by one plugin. */
    default void clearPlugin(@NotNull String plugin) {
    }

    /**
     * Records a bounded diagnostic event for the given plugin.
     *
     * @param plugin owning plugin name
     * @param level event severity
     * @param component component identifier
     * @param code short machine-readable code, e.g. {@code "DB_TIMEOUT"}
     * @param message human-readable message
     * @param error optional throwable whose stack trace is captured immediately
     * @param fields arbitrary structured fields
     */
    void record(@NotNull String plugin, @NotNull DiagnosticLevel level, @NotNull String component, @NotNull String code,
                @NotNull String message, @Nullable Throwable error, @NotNull Map<String, Object> fields);

    /** Like the full call, with no error and no fields. */
    default void record(@NotNull String plugin, @NotNull DiagnosticLevel level, @NotNull String component, @NotNull String code,
                        @NotNull String message) {
        record(plugin, level, component, code, message, null, Collections.emptyMap());
    }

    /** Like the full call, with no fields. */
    default void record(@NotNull String plugin, @NotNull DiagnosticLevel level, @NotNull String component, @NotNull String code,
                        @NotNull String message, @Nullable Throwable error) {
        record(plugin, level, component, code, message, error, Collections.emptyMap());
    }
}
