package ru.privatenull.pnlibrary.api.observability

import java.nio.file.Path
import java.util.UUID

/** Describes the operational importance and retention priority of an observation. */
enum class ObservationLevel {
    /** Detailed execution information intended for short-lived troubleshooting. */
    TRACE,

    /** Normal operational information. */
    INFO,

    /** A noteworthy state change that is not a failure. */
    NOTICE,

    /** A recoverable problem or degraded state. */
    WARNING,

    /** A failed operation that requires investigation. */
    ERROR,

    /** A severe incident retained independently of the normal history window. */
    CRITICAL,
}

/**
 * Complete input for recording one observation.
 *
 * Files are copied when the request is recorded. The journal stores generated
 * attachment identifiers rather than the original absolute paths.
 *
 * @property plugin optional logical plugin name
 * @property source optional component or operation that produced the event
 * @property message concise human-readable description
 * @property level severity and retention priority
 * @property data bounded searchable metadata
 * @property files local files to attach to this observation
 * @property error optional failure captured as type and message
 */
data class ObservationRequest(
    val plugin: String? = null,
    val source: String? = null,
    val message: String,
    val level: ObservationLevel = ObservationLevel.INFO,
    val data: Map<String, String> = emptyMap(),
    val files: List<Path> = emptyList(),
    val error: Throwable? = null,
)

/**
 * Immutable event stored in the shared observability journal.
 *
 * @property id globally unique observation identifier
 * @property timestamp creation time expressed as Unix epoch milliseconds
 * @property plugin logical plugin name, when known
 * @property source component or operation, when known
 * @property message concise human-readable description
 * @property level severity and retention priority
 * @property data bounded searchable metadata
 * @property errorType fully qualified throwable type, when present
 * @property errorMessage throwable message, when present
 */
data class Observation(
    val id: String,
    val timestamp: Long,
    val plugin: String?,
    val source: String?,
    val message: String,
    val level: ObservationLevel,
    val data: Map<String, String>,
    val errorType: String?,
    val errorMessage: String?,
) {
    /** Factory methods for converting mutable requests into immutable records. */
    companion object {
        /** Creates a new observation with a generated identifier. */
        @JvmStatic
        @JvmOverloads
        fun from(request: ObservationRequest, timestamp: Long = System.currentTimeMillis()): Observation = Observation(
            id = UUID.randomUUID().toString(),
            timestamp = timestamp,
            plugin = request.plugin?.take(MAX_ID_LENGTH),
            source = request.source?.take(MAX_ID_LENGTH),
            message = request.message.take(MAX_MESSAGE_LENGTH),
            level = request.level,
            data = request.data.entries.take(MAX_DATA_ENTRIES).associate { (key, value) ->
                key.take(MAX_DATA_KEY_LENGTH) to value.take(MAX_DATA_VALUE_LENGTH)
            },
            errorType = request.error?.javaClass?.name,
            errorMessage = request.error?.message?.take(MAX_ERROR_MESSAGE_LENGTH),
        )

        private const val MAX_ID_LENGTH = 128
        private const val MAX_MESSAGE_LENGTH = 1_024
        private const val MAX_DATA_ENTRIES = 32
        private const val MAX_DATA_KEY_LENGTH = 128
        private const val MAX_DATA_VALUE_LENGTH = 2_048
        private const val MAX_ERROR_MESSAGE_LENGTH = 4_096
    }
}
