package ru.privatenull.pnlibrary.api.plugin

import java.util.Locale

/**
 * Validated, platform-independent identity of a plugin using pnLibrary.
 *
 * IDs are case-insensitive and normalized to lowercase. They may contain ASCII
 * letters, digits, dots, underscores, and hyphens, must start with a letter or
 * digit, and may not exceed 64 characters.
 */
class PluginId private constructor(
    /** Normalized lowercase identifier used as the registry key. */
    val value: String,
) {
    /** Compares normalized identifier values. */
    override fun equals(other: Any?): Boolean = other is PluginId && value == other.value
    /** Returns the normalized identifier's hash code. */
    override fun hashCode(): Int = value.hashCode()
    /** Returns [value] for logs, diagnostics, and canonical references. */
    override fun toString(): String = value

    /** Validation and normalization entry point for plugin identifiers. */
    companion object {
        private val FORMAT = Regex("[a-z0-9][a-z0-9_.-]{0,63}")

        /** Creates a normalized ID or rejects an invalid value. */
        @JvmStatic
        fun of(value: String): PluginId {
            val normalized = value.trim().lowercase(Locale.ROOT)
            require(FORMAT.matches(normalized)) {
                "pluginId must match ${FORMAT.pattern}"
            }
            return PluginId(normalized)
        }
    }
}
