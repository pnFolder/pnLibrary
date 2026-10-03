package ru.privatenull.pnlibrary.api.observability

import java.nio.file.Path
import java.util.UUID

enum class ObservationLevel {
    TRACE,
    INFO,
    NOTICE,
    WARNING,
    ERROR,
    CRITICAL,
}

data class ObservationRequest(
    val plugin: String? = null,
    val source: String? = null,
    val message: String,
    val level: ObservationLevel = ObservationLevel.INFO,
    val data: Map<String, String> = emptyMap(),
    val files: List<Path> = emptyList(),
    val error: Throwable? = null,
)

data class Observation(
    val id: String,
    val timestamp: Long,
    val plugin: String?,
    val source: String?,
    val message: String,
    val level: ObservationLevel,
    val data: Map<String, String>,
    val files: List<Path>,
    val errorType: String?,
    val errorMessage: String?,
) {
    companion object {
        @JvmStatic
        fun from(request: ObservationRequest, timestamp: Long = System.currentTimeMillis()): Observation = Observation(
            id = UUID.randomUUID().toString(),
            timestamp = timestamp,
            plugin = request.plugin,
            source = request.source,
            message = request.message,
            level = request.level,
            data = request.data,
            files = request.files,
            errorType = request.error?.javaClass?.name,
            errorMessage = request.error?.message,
        )
    }
}
