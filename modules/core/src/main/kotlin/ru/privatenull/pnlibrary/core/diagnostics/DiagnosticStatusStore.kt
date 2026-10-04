package ru.privatenull.pnlibrary.core.diagnostics

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Latest component status values for one plugin. */
internal class DiagnosticStatusStore(
    private val sanitizer: DiagnosticValueSanitizer,
) {
    private val statuses = ConcurrentHashMap<String, Map<String, Any?>>()

    fun update(
        component: String,
        state: String,
        detail: String,
        fields: Map<String, Any?>,
    ) {
        statuses[sanitizer.key(component)] = linkedMapOf(
            "state" to sanitizer.text(state, MAXIMUM_STATE_LENGTH),
            "detail" to sanitizer.text(detail, MAXIMUM_DETAIL_LENGTH),
            "updatedUtc" to Instant.now().toString(),
            "fields" to sanitizer.map(fields),
        )
    }

    fun remove(component: String) {
        statuses.remove(sanitizer.key(component))
    }

    fun snapshot(): Map<String, Map<String, Any?>> = LinkedHashMap(statuses)

    private companion object {
        const val MAXIMUM_STATE_LENGTH = 64
        const val MAXIMUM_DETAIL_LENGTH = 2_048
    }
}
