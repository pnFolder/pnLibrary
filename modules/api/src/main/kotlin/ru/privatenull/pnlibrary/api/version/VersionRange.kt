package ru.privatenull.pnlibrary.api.version

/**
 * Semantic version range with inclusive or exclusive boundaries.
 *
 * Kotlin: `VersionRange.atLeast("2.0.0-beta.2").contains(version)`.
 * Java: `VersionRange.closed("2.0.0", "3.0.0").contains(version)`.
 */
class VersionRange private constructor(
    /** Lower boundary, or `null` when unbounded below. */
    val minimum: SemanticVersion?,
    /** Upper boundary, or `null` when unbounded above. */
    val maximum: SemanticVersion?,
    /** Whether [minimum] itself is included. Ignored when no minimum exists. */
    val includeMinimum: Boolean,
    /** Whether [maximum] itself is included. Ignored when no maximum exists. */
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

    /** Factories for unbounded, one-sided, and closed semantic-version ranges. */
    companion object {
        /** Creates an unbounded range containing every valid semantic version. */
        @JvmStatic
        fun any(): VersionRange = VersionRange(null, null, true, true)

        /** Creates the range `[minimum, +infinity)`. */
        @JvmStatic
        fun atLeast(minimum: String): VersionRange =
            VersionRange(SemanticVersion.parse(minimum), null, true, true)

        /** Creates the range `(minimum, +infinity)`. */
        @JvmStatic
        fun greaterThan(minimum: String): VersionRange =
            VersionRange(SemanticVersion.parse(minimum), null, false, true)

        /** Creates the inclusive range `[minimum, maximum]`. */
        @JvmStatic
        fun closed(minimum: String, maximum: String): VersionRange =
            VersionRange(SemanticVersion.parse(minimum), SemanticVersion.parse(maximum), true, true)

        /** Creates the range `(-infinity, maximum)`. */
        @JvmStatic
        fun until(maximum: String): VersionRange =
            VersionRange(null, SemanticVersion.parse(maximum), true, false)
    }
}
