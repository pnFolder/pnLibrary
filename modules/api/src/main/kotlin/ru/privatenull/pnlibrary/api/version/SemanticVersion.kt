package ru.privatenull.pnlibrary.api.version

/**
 * Platform-independent semantic version.
 *
 * Implements SemVer 2.0.0 precedence for the numeric core and arbitrary prerelease identifiers.
 * Numeric identifiers are compared numerically, so `beta.10` is newer than `beta.2`. Build
 * metadata is validated but omitted from this value because it does not affect precedence.
 *
 * Kotlin: `SemanticVersion.parse("2.0.0-beta.2").isAtLeast("2.0.0-beta.1")`.
 * Java: `SemanticVersion.parse("2.0.0").isAtLeast("1.9.0")`.
 */
class SemanticVersion private constructor(
    /** Major compatibility version. */
    val major: Int,
    /** Minor feature version. */
    val minor: Int,
    /** Patch correction version. */
    val patch: Int,
    /** Ordered prerelease identifiers, empty for a stable release. */
    val prerelease: List<String>,
) : Comparable<SemanticVersion> {

    /** Returns `true` when this version is not older than [minimum]. */
    fun isAtLeast(minimum: SemanticVersion): Boolean = this >= minimum

    /** Convenience overload accepting a version string. */
    fun isAtLeast(minimum: String): Boolean = isAtLeast(parse(minimum))

    /** Returns whether this version belongs to [range]. */
    fun isIn(range: VersionRange): Boolean = range.contains(this)

    /** Compares precedence according to SemVer 2.0.0, excluding build metadata. */
    override fun compareTo(other: SemanticVersion): Int {
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)
            .takeIf { it != 0 }?.let { return it }
        if (prerelease.isEmpty()) return if (other.prerelease.isEmpty()) 0 else 1
        if (other.prerelease.isEmpty()) return -1
        for (index in 0 until maxOf(prerelease.size, other.prerelease.size)) {
            val left = prerelease.getOrNull(index) ?: return -1
            val right = other.prerelease.getOrNull(index) ?: return 1
            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            val result = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
            if (result != 0) return result
        }
        return 0
    }

    /** Returns whether two versions have equal SemVer precedence. */
    override fun equals(other: Any?): Boolean = other is SemanticVersion && compareTo(other) == 0
    /** Hashes exactly the fields that participate in [equals]. */
    override fun hashCode(): Int = listOf(major, minor, patch, prerelease).hashCode()
    /** Returns the normalized SemVer string, including prerelease and build metadata. */
    override fun toString(): String = "$major.$minor.$patch" +
        prerelease.takeIf(List<String>::isNotEmpty)?.joinToString(".", prefix = "-").orEmpty()

    /** Strict SemVer parsing and validation operations. */
    companion object {
        private val FORMAT = Regex(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
                "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?" +
                "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$",
        )

        /** Parses SemVer, allowing a leading `v` and build metadata. */
        @JvmStatic
        fun parse(raw: String): SemanticVersion = tryParse(raw)
            ?: throw IllegalArgumentException("Invalid semantic version: $raw")

        /** Returns a parsed version, or `null` for invalid input. */
        @JvmStatic
        fun tryParse(raw: String): SemanticVersion? {
            val value = raw.trim().removePrefix("v")
            val match = FORMAT.matchEntire(value) ?: return null
            val prerelease = match.groupValues[4].split('.').filter(String::isNotBlank)
            if (prerelease.any { it.length > 1 && it.all(Char::isDigit) && it.startsWith('0') }) return null
            return SemanticVersion(
                match.groupValues[1].toIntOrNull() ?: return null,
                match.groupValues[2].toIntOrNull() ?: return null,
                match.groupValues[3].toIntOrNull() ?: return null,
                prerelease,
            )
        }
    }
}
