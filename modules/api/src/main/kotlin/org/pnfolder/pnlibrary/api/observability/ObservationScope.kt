package org.pnfolder.pnlibrary.api.observability

import java.nio.file.Path
import java.time.Duration

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
    private var keepFor: Duration? = null

    /** Overrides the plugin name the observation is attributed to. */
    fun plugin(value: String): ObservationScope = apply {
        plugin = value
    }

    /** Names the component that produced the observation. */
    fun source(value: String): ObservationScope = apply {
        source = value
    }

    /** Sets the observation text. */
    fun message(value: String): ObservationScope = apply {
        message = value
    }

    /** Sets the severity, `INFO` by default. */
    fun level(value: ObservationLevel): ObservationScope = apply {
        level = value
    }

    /** Attaches key-value details; values are stored as text. */
    fun data(vararg values: Pair<String, Any?>): ObservationScope = apply {
        values.forEach { (key, value) -> data[key] = value?.toString() ?: "null" }
    }

    /** Attaches files to include with the observation. */
    fun files(vararg paths: Path): ObservationScope = apply {
        files += paths
    }

    /**
     * Keeps the observation for [duration] regardless of its level, at most [MAX_KEEP_DAYS] days.
     *
     * The period is set by the plugin author in code; without it retention follows the level.
     */
    fun keepFor(duration: Duration): ObservationScope = apply {
        require(!duration.isNegative && !duration.isZero) { "Retention must be positive" }
        require(duration <= Duration.ofDays(MAX_KEEP_DAYS)) { "Retention must not exceed $MAX_KEEP_DAYS days" }
        keepFor = duration
    }

    internal fun build(error: Throwable? = null): ObservationRequest = ObservationRequest(
        plugin = plugin,
        source = source,
        message = if (message == "Diagnostic observation" && error?.message != null) error.message!! else message,
        level = if (error == null) level else ObservationLevel.ERROR,
        data = data.toMap() + listOfNotNull(keepFor?.let { ObservationKeys.KEEP_FOR_MS to it.toMillis().toString() }),
        files = files.toList(),
        error = error,
    )

    private companion object {
        const val MAX_KEEP_DAYS = 3650L
    }
}

/** Reserved [Observation.data] keys used by the library for retention. */
object ObservationKeys {
    /** Request: how long to keep the observation, in milliseconds; replaced by [KEEP_UNTIL_MS] when stored. */
    const val KEEP_FOR_MS: String = "pn.keepForMs"

    /** Stored: epoch milliseconds after which the observation is dropped. */
    const val KEEP_UNTIL_MS: String = "pn.keepUntilMs"
}
