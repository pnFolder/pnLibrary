package org.pnfolder.pnlibrary.api.observability

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

    fun plugin(value: String): ObservationScope = apply {
        plugin = value
    }

    fun source(value: String): ObservationScope = apply {
        source = value
    }

    fun message(value: String): ObservationScope = apply {
        message = value
    }

    fun level(value: ObservationLevel): ObservationScope = apply {
        level = value
    }

    fun data(vararg values: Pair<String, Any?>): ObservationScope = apply {
        values.forEach { (key, value) -> data[key] = value?.toString() ?: "null" }
    }

    fun files(vararg paths: Path): ObservationScope = apply {
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
