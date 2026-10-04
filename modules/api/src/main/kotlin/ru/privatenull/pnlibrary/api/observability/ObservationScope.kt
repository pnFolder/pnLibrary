package ru.privatenull.pnlibrary.api.observability

import java.nio.file.Path

/**
 * Kotlin DSL used to assemble one [ObservationRequest].
 *
 * The scope is created by [ObservabilityService.capture] or
 * [ObservabilityService.failure]; callers do not retain it.
 */
class ObservationScope internal constructor() {
    private var plugin: String? = null
    private var source: String? = null
    private var message: String = "Diagnostic observation"
    private var level: ObservationLevel = ObservationLevel.INFO
    private val data = linkedMapOf<String, String>()
    private val files = mutableListOf<Path>()

    /** Sets the logical plugin that owns the observation. */
    fun plugin(value: String) = apply {
        plugin = value
    }

    /** Sets the component or operation that produced the observation. */
    fun source(value: String) = apply {
        source = value
    }

    /** Sets the concise human-readable description. */
    fun message(value: String) = apply {
        message = value
    }

    /** Sets the severity and retention priority. */
    fun level(value: ObservationLevel) = apply {
        level = value
    }

    /** Adds structured values after converting them to safe strings. */
    fun data(vararg values: Pair<String, Any?>) = apply {
        values.forEach { (key, value) -> data[key] = value?.toString() ?: "null" }
    }

    /** Adds local files that will be copied as attachments when recorded. */
    fun files(vararg paths: Path) = apply {
        files += paths
    }

    internal fun build(error: Throwable? = null): ObservationRequest = ObservationRequest(
        plugin = plugin,
        source = source,
        message = if (message == "Diagnostic observation" && error?.message != null) error.message!! else message,
        level = if (error == null) level else ObservationLevel.ERROR,
        data = data.toMap(),
        files = files.toList(),
        error = error,
    )
}
