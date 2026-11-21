package org.pnfolder.pnlibrary.api.plugin

import java.util.Locale

/** Validated, case-insensitive local identity of a logical module. */
class ModuleId private constructor(
    /** Normalized lowercase module identifier. */
    val value: String,
) {
    /** Returns whether [other] contains the same normalized identifier. */
    override fun equals(other: Any?): Boolean = other is ModuleId && value == other.value
    /** Returns the normalized identifier hash. */
    override fun hashCode(): Int = value.hashCode()
    /** Returns [value] without additional formatting. */
    override fun toString(): String = value

    companion object {
        private val FORMAT = Regex("[a-z0-9][a-z0-9_.-]{0,63}")

        /** Normalizes and validates [value]. */
        @JvmStatic
        fun of(value: String): ModuleId {
            val normalized = value.trim().lowercase(Locale.ROOT)
            require(FORMAT.matches(normalized)) { "Module ID must match ${FORMAT.pattern}" }
            return ModuleId(normalized)
        }
    }
}
