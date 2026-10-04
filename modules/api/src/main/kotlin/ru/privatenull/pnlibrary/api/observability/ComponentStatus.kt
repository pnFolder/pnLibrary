package ru.privatenull.pnlibrary.api.observability

/**
 * Describes the latest known state of one runtime component.
 *
 * A new value for the same [plugin] and [component] replaces the previous
 * value. Status entries are intended for current state; use [Observation]
 * when a chronological event must remain in the diagnostic history.
 *
 * @property plugin logical name of the plugin that owns the component
 * @property component stable component identifier, such as `database`
 * @property state short machine-readable state, such as `ready` or `degraded`
 * @property detail optional human-readable explanation of the current state
 * @property data additional bounded metadata for support reports
 * @property updatedAt creation time expressed as Unix epoch milliseconds
 */
data class ComponentStatus(
    val plugin: String,
    val component: String,
    val state: String,
    val detail: String = "",
    val data: Map<String, String> = emptyMap(),
    val updatedAt: Long = System.currentTimeMillis(),
)
