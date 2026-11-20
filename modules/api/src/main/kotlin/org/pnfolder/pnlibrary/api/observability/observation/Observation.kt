package org.pnfolder.pnlibrary.api.observability.observation

import java.util.UUID

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
    companion object {
        /** Creates an observation from [request] stamped with [timestamp] (epoch milliseconds). */
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
