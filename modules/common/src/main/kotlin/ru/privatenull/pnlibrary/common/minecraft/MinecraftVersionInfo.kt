package ru.privatenull.pnlibrary.common.minecraft

/**
 * Result of parsing a server version string.
 *
 * The enum is deliberately still [MinecraftVersion.UNKNOWN] when the release
 * is not known by this library, but the original value and extracted numeric
 * coordinates remain available for forward-compatible checks.
 *
 * @property raw original server-provided version string
 * @property parsed known enum value, or [MinecraftVersion.UNKNOWN]
 * @property major extracted major component, when numeric parsing succeeded
 * @property minor extracted minor component, when numeric parsing succeeded
 * @property patch extracted patch component, defaulted to zero when omitted
 */
data class MinecraftVersionInfo(
    val raw: String,
    val parsed: MinecraftVersion,
    val major: Int?,
    val minor: Int?,
    val patch: Int?,
) {
    /** Whether [parsed] is represented by this pnLibrary release. */
    val known: Boolean get() = parsed.known

    /** `true` when a numeric version was found, even if the library does not know it yet. */
    val numeric: Boolean get() = major != null && minor != null

    /** Numeric comparison for unknown future releases; returns `null` when either side is not numeric. */
    fun compareNumeric(other: MinecraftVersionInfo): Int? {
        if (!numeric || !other.numeric) return null
        return compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch ?: 0 })
    }
}
