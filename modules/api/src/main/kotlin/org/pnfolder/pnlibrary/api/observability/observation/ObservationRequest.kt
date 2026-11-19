package org.pnfolder.pnlibrary.api.observability.observation

import java.nio.file.Path

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
