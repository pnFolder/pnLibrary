package ru.privatenull.pnlibrary.api.version

/**
 * Semantic version range with inclusive or exclusive boundaries.
 *
 * Kotlin: `VersionRange.atLeast("2.0.0-beta.2").contains(version)`.
 * Java: `VersionRange.closed("2.0.0", "3.0.0").contains(version)`.
 */
class VersionRange private constructor(
    val minimum: SemanticVersion?,
    val maximum: SemanticVersion?,
    val includeMinimum: Boolean,
    val includeMaximum: Boolean,
) {
    init {
        require(minimum == null || maximum == null || minimum <= maximum) {
            "minimum version must not be newer than maximum version"
        }
    }
    /** Returns whether [version] belongs to this range. */
    operator fun contains(version: SemanticVersion): Boolean {
        val afterMinimum = minimum == null || if (includeMinimum) version >= minimum else version > minimum
        val beforeMaximum = maximum == null || if (includeMaximum) version <= maximum else version < maximum
        return afterMinimum && beforeMaximum
    }

    /** Parses and checks a version string. */
    fun contains(version: String): Boolean = contains(SemanticVersion.parse(version))

    companion object {
        @JvmStatic fun any(): VersionRange = VersionRange(null, null, true, true)
        @JvmStatic fun atLeast(minimum: String): VersionRange = VersionRange(SemanticVersion.parse(minimum), null, true, true)
        @JvmStatic fun greaterThan(minimum: String): VersionRange = VersionRange(SemanticVersion.parse(minimum), null, false, true)
        @JvmStatic fun closed(minimum: String, maximum: String): VersionRange =
            VersionRange(SemanticVersion.parse(minimum), SemanticVersion.parse(maximum), true, true)
        @JvmStatic fun until(maximum: String): VersionRange = VersionRange(null, SemanticVersion.parse(maximum), true, false)
    }
}
