package ru.privatenull.pnlibrary.bukkit.version

/**
 * Minecraft version range with configurable boundaries.
 *
 * A missing [minimum] means there is no lower bound; a missing [maximum] means
 * there is no upper bound. [MinecraftVersion.UNKNOWN] cannot be a boundary and
 * never belongs to a range.
 *
 * Prefer the factory methods in [Companion]:
 * ```kotlin
 * val legacy = MinecraftVersionRange.between(V1_8_8, V1_12_2)
 * val modern = MinecraftVersionRange.atLeast(V1_20_5)
 * if (MinecraftVersion.parse("1.21.11") in modern) enableModernApi()
 * ```
 * Java:
 * ```java
 * MinecraftVersionRange legacy = MinecraftVersionRange.between(
 *     MinecraftVersion.V1_8_8,
 *     MinecraftVersion.V1_12_2
 * );
 * if (legacy.contains(MinecraftVersion.parse("1.21.11"))) {
 *     enableLegacyApi();
 * }
 * ```
 *
 * @property minimum lower boundary, or `null`.
 * @property maximum upper boundary, or `null`.
 * @property includeMinimum whether the lower boundary is included.
 * @property includeMaximum whether the upper boundary is included.
 */
data class MinecraftVersionRange(
    val minimum: MinecraftVersion?,
    val maximum: MinecraftVersion?,
    val includeMinimum: Boolean,
    val includeMaximum: Boolean,
) {
    init {
        require(minimum?.known != false && maximum?.known != false) { "UNKNOWN cannot be a range boundary" }
        if (minimum != null && maximum != null) {
            require(!minimum.isNewerThan(maximum)) { "Minimum ${minimum.text} is newer than maximum ${maximum.text}" }
        }
    }

    /**
     * Supports `version in range` in Kotlin. Java calls it as a regular method:
     * ```java
     * if (range.contains(version)) enableAdapter();
     * ```
     */
    operator fun contains(version: MinecraftVersion): Boolean {
        if (!version.known) return false
        val afterMinimum = minimum == null || if (includeMinimum) version.isAtLeast(minimum) else version.isNewerThan(minimum)
        val beforeMaximum = maximum == null || if (includeMaximum) version.isAtMost(maximum) else version.isOlderThan(maximum)
        return afterMinimum && beforeMaximum
    }
    /** Returns `true` when [version] does not belong to this range. */
    fun excludes(version: MinecraftVersion): Boolean = version !in this

    /** Returns `true` when this range contains exactly one version. */
    fun isExact(): Boolean = minimum != null && minimum == maximum && includeMinimum && includeMaximum

    /**
     * Returns whether this range and [other] share at least one version.
     * ```java
     * if (supported.overlaps(featureVersions)) registerFeature();
     * ```
     */
    fun overlaps(other: MinecraftVersionRange): Boolean = intersection(other) != null

    /**
     * Returns the intersection with [other], or `null` when none exists.
     * Inclusive and exclusive boundaries are preserved.
     * ```java
     * MinecraftVersionRange common = supported.intersection(featureVersions);
     * if (common != null) logger.info("Shared versions: " + common);
     * ```
     */
    fun intersection(other: MinecraftVersionRange): MinecraftVersionRange? {
        val min = newer(minimum, other.minimum)
        val max = older(maximum, other.maximum)
        if (min != null && max != null && min.isNewerThan(max)) return null
        val includeMin = boundIncluded(min, true, this) && boundIncluded(min, true, other)
        val includeMax = boundIncluded(max, false, this) && boundIncluded(max, false, other)
        if (min != null && min == max && !(includeMin && includeMax)) return null
        return MinecraftVersionRange(min, max, includeMin, includeMax)
    }

    /** Formats a value such as `[1.8.8, 1.12.2]`, `>=1.20.5`, or `<1.13`. */
    override fun toString(): String = when {
        minimum == null && maximum == null -> "all known versions"
        minimum == null -> "${if (includeMaximum) "<=" else "<"}${maximum!!.text}"
        maximum == null -> "${if (includeMinimum) ">=" else ">"}${minimum.text}"
        else -> "${if (includeMinimum) "[" else "("}${minimum.text}, ${maximum.text}${if (includeMaximum) "]" else ")"}"
    }

    companion object {
        /**
         * Inclusive range `[minimum, maximum]`.
         * ```java
         * MinecraftVersionRange range = MinecraftVersionRange.between(
         *     MinecraftVersion.V1_16_5,
         *     MinecraftVersion.V1_21_11
         * );
         * ```
         */
        @JvmStatic fun between(minimum: MinecraftVersion, maximum: MinecraftVersion) =
            MinecraftVersionRange(minimum, maximum, true, true)

        /**
         * Exclusive range `(minimum, maximum)`.
         * ```java
         * MinecraftVersionRange range = MinecraftVersionRange.betweenExclusive(
         *     MinecraftVersion.V1_16_5,
         *     MinecraftVersion.V1_21
         * );
         * ```
         */
        @JvmStatic fun betweenExclusive(minimum: MinecraftVersion, maximum: MinecraftVersion) =
            MinecraftVersionRange(minimum, maximum, false, false)

        /**
         * Range from [minimum] without an upper boundary. Java may pass the
         * second argument or use the overload:
         * ```java
         * MinecraftVersionRange inclusive = MinecraftVersionRange.from(MinecraftVersion.V1_20_5);
         * MinecraftVersionRange exclusive = MinecraftVersionRange.from(MinecraftVersion.V1_20_5, false);
         * ```
         */
        @JvmStatic @JvmOverloads fun from(minimum: MinecraftVersion, inclusive: Boolean = true) =
            MinecraftVersionRange(minimum, null, inclusive, false)

        /** Range up to [maximum] without a lower boundary. */
        @JvmStatic @JvmOverloads fun until(maximum: MinecraftVersion, inclusive: Boolean = true) =
            MinecraftVersionRange(null, maximum, false, inclusive)

        /** Range `[minimum, +∞)`. */
        @JvmStatic fun atLeast(minimum: MinecraftVersion) = from(minimum, true)

        /** Range `(minimum, +∞)`. */
        @JvmStatic fun newerThan(minimum: MinecraftVersion) = from(minimum, false)

        /** Range `(-∞, maximum]`. */
        @JvmStatic fun atMost(maximum: MinecraftVersion) = until(maximum, true)

        /** Range `(-∞, maximum)`. */
        @JvmStatic fun olderThan(maximum: MinecraftVersion) = until(maximum, false)

        /** Range containing only [version]. */
        @JvmStatic fun exact(version: MinecraftVersion) = MinecraftVersionRange(version, version, true, true)

        /** Every known version; [MinecraftVersion.UNKNOWN] is excluded. */
        @JvmStatic fun allKnown() = MinecraftVersionRange(null, null, false, false)

        private fun newer(a: MinecraftVersion?, b: MinecraftVersion?): MinecraftVersion? = when {
            a == null -> b; b == null -> a; a.isAtLeast(b) -> a; else -> b
        }
        private fun older(a: MinecraftVersion?, b: MinecraftVersion?): MinecraftVersion? = when {
            a == null -> b; b == null -> a; a.isAtMost(b) -> a; else -> b
        }
        private fun boundIncluded(value: MinecraftVersion?, minimum: Boolean, range: MinecraftVersionRange): Boolean {
            if (value == null) return true
            return if (minimum) {
                range.minimum == null || value != range.minimum || range.includeMinimum
            } else {
                range.maximum == null || value != range.maximum || range.includeMaximum
            }
        }
    }
}
