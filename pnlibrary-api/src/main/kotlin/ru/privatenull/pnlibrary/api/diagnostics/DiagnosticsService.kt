package ru.privatenull.pnlibrary.api.diagnostics

import java.nio.file.Path

/**
 * Public diagnostic service exposed through [ru.privatenull.pnlibrary.api.runtime.PnLibrary].
 *
 * ### Usage from Kotlin
 * ```kotlin
 * val service = PnLibraryProvider.get().diagnostics
 * val reg = service.register("pnMarket", DiagnosticContainer.builder("auction")
 *     .snapshot(Supplier { mapOf(...) }).build())
 * ```
 *
 * ### Usage from Java
 * ```java
 * DiagnosticsService svc = PnLibraryProvider.get().getDiagnostics();
 * DiagnosticRegistration reg = svc.register("pnMarket",
 *     DiagnosticContainer.builder("auction").snapshot(() -> Map.of(...)).build());
 * ```
 */
interface DiagnosticsService {

    /** Incremented when the API contract changes incompatibly. */
    val apiVersion: Int get() = API_VERSION

    /**
     * Registers a [DiagnosticsContributor] (or [DiagnosticContainer]) under the given plugin name.
     *
     * @param plugin     Logical plugin name, case-insensitive (e.g. `"pnMarket"`).
     * @param contributor The contributor supplying diagnostic snapshots.
     * @return A [DiagnosticRegistration] that removes the contributor when closed.
     */
    fun register(plugin: String, contributor: DiagnosticsContributor): DiagnosticRegistration
    fun register(plugin: String, dataDirectory: Path, contributor: DiagnosticsContributor): DiagnosticRegistration =
        register(plugin, contributor)

    /**
     * Updates or creates a component status entry visible in diagnostic reports.
     *
     * @param plugin    Owning plugin name.
     * @param component Component identifier, e.g. `"database"`.
     * @param state     Short status string, e.g. `"CONNECTED"` or `"DEGRADED"`.
     * @param detail    Optional human-readable detail; may be empty.
     * @param fields    Arbitrary structured fields attached to the status entry.
     */
    fun status(
        plugin: String,
        component: String,
        state: String,
        detail: String = "",
        fields: Map<String, Any?> = emptyMap(),
    )

    /** Removes the component status entry previously set by [status]. */
    fun clearStatus(plugin: String, component: String)

    /** Removes every contributor, status and event owned by one plugin. */
    fun clearPlugin(plugin: String) = Unit

    /**
     * Records a bounded diagnostic event for the given plugin.
     *
     * @param plugin    Owning plugin name.
     * @param level     Event severity.
     * @param component Component identifier.
     * @param code      Short machine-readable code, e.g. `"DB_TIMEOUT"`.
     * @param message   Human-readable message.
     * @param error     Optional throwable whose stack trace is captured immediately.
     * @param fields    Arbitrary structured fields.
     */
    fun record(
        plugin: String,
        level: DiagnosticLevel,
        component: String,
        code: String,
        message: String,
        error: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    )

    companion object {
        const val API_VERSION: Int = 2
    }
}
