package ru.privatenull.pnlibrary.common.minecraft

/**
 * Result of parsing a server version string.
 *
 * The enum is deliberately still [MinecraftVersion.UNKNOWN] when the release
 * is not known by this library, but the original value and extracted numeric
 * coordinates remain available for forward-compatible checks.
 */
data class MinecraftVersionInfo(
    val raw: String,
    val parsed: MinecraftVersion,
    val major: Int?,
    val minor: Int?,
    val patch: Int?,
) {
    val known: Boolean get() = parsed.known

    /** `true` when a numeric version was found, even if the library does not know it yet. */
    val numeric: Boolean get() = major != null && minor != null

    /** Numeric comparison for unknown future releases; returns `null` when either side is not numeric. */
    fun compareNumeric(other: MinecraftVersionInfo): Int? {
        if (!numeric || !other.numeric) return null
        return compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch ?: 0 })
    }
}
