package ru.privatenull.pnlibrary.api.version.minecraft


/**
 * Known Minecraft version that can be compared on any JVM platform.
 *
 * Comparisons use [major], [minor], and [patch], never [Enum.ordinal]. Adding
 * enum constants therefore cannot change existing ordering. [UNKNOWN] represents
 * a release that has not been added to the library yet.
 *
 * Kotlin:
 * ```kotlin
 * val version = MinecraftVersion.parse("1.21.11")
 * if (version.isAtLeast(MinecraftVersion.V1_20_5)) enableComponents()
 * ```
 * Java:
 * ```java
 * MinecraftVersion version = MinecraftVersion.parse("1.21.11");
 * if (version.isAtLeast(MinecraftVersion.V1_20_5)) {
 *     enableComponents();
 * }
 * ```
 *
 * @property text canonical representation, for example `1.21.11`.
 * @property major first numeric component.
 * @property minor second numeric component.
 * @property patch third numeric component, or `0` when omitted.
 */
enum class MinecraftVersion(val text: String, val major: Int, val minor: Int, val patch: Int) {
    V1_8("1.8", 1, 8, 0), V1_8_1("1.8.1", 1, 8, 1), V1_8_2("1.8.2", 1, 8, 2),
    V1_8_3("1.8.3", 1, 8, 3), V1_8_4("1.8.4", 1, 8, 4), V1_8_5("1.8.5", 1, 8, 5),
    V1_8_6("1.8.6", 1, 8, 6), V1_8_7("1.8.7", 1, 8, 7), V1_8_8("1.8.8", 1, 8, 8),
    V1_8_9("1.8.9", 1, 8, 9),
    V1_9("1.9", 1, 9, 0), V1_9_1("1.9.1", 1, 9, 1), V1_9_2("1.9.2", 1, 9, 2),
    V1_9_3("1.9.3", 1, 9, 3), V1_9_4("1.9.4", 1, 9, 4),
    V1_10("1.10", 1, 10, 0), V1_10_1("1.10.1", 1, 10, 1), V1_10_2("1.10.2", 1, 10, 2),
    V1_11("1.11", 1, 11, 0), V1_11_1("1.11.1", 1, 11, 1), V1_11_2("1.11.2", 1, 11, 2),
    V1_12("1.12", 1, 12, 0), V1_12_1("1.12.1", 1, 12, 1), V1_12_2("1.12.2", 1, 12, 2),
    V1_13("1.13", 1, 13, 0), V1_13_1("1.13.1", 1, 13, 1), V1_13_2("1.13.2", 1, 13, 2),
    V1_14("1.14", 1, 14, 0), V1_14_1("1.14.1", 1, 14, 1), V1_14_2("1.14.2", 1, 14, 2),
    V1_14_3("1.14.3", 1, 14, 3), V1_14_4("1.14.4", 1, 14, 4),
    V1_15("1.15", 1, 15, 0), V1_15_1("1.15.1", 1, 15, 1), V1_15_2("1.15.2", 1, 15, 2),
    V1_16("1.16", 1, 16, 0), V1_16_1("1.16.1", 1, 16, 1), V1_16_2("1.16.2", 1, 16, 2),
    V1_16_3("1.16.3", 1, 16, 3), V1_16_4("1.16.4", 1, 16, 4), V1_16_5("1.16.5", 1, 16, 5),
    V1_17("1.17", 1, 17, 0), V1_17_1("1.17.1", 1, 17, 1),
    V1_18("1.18", 1, 18, 0), V1_18_1("1.18.1", 1, 18, 1), V1_18_2("1.18.2", 1, 18, 2),
    V1_19("1.19", 1, 19, 0), V1_19_1("1.19.1", 1, 19, 1), V1_19_2("1.19.2", 1, 19, 2),
    V1_19_3("1.19.3", 1, 19, 3), V1_19_4("1.19.4", 1, 19, 4),
    V1_20("1.20", 1, 20, 0), V1_20_1("1.20.1", 1, 20, 1), V1_20_2("1.20.2", 1, 20, 2),
    V1_20_3("1.20.3", 1, 20, 3), V1_20_4("1.20.4", 1, 20, 4), V1_20_5("1.20.5", 1, 20, 5),
    V1_20_6("1.20.6", 1, 20, 6),
    V1_21("1.21", 1, 21, 0), V1_21_1("1.21.1", 1, 21, 1), V1_21_2("1.21.2", 1, 21, 2),
    V1_21_3("1.21.3", 1, 21, 3), V1_21_4("1.21.4", 1, 21, 4), V1_21_5("1.21.5", 1, 21, 5),
    V1_21_6("1.21.6", 1, 21, 6), V1_21_7("1.21.7", 1, 21, 7), V1_21_8("1.21.8", 1, 21, 8),
    V1_21_9("1.21.9", 1, 21, 9), V1_21_10("1.21.10", 1, 21, 10), V1_21_11("1.21.11", 1, 21, 11),
    V26_1("26.1", 26, 1, 0), V26_2("26.2", 26, 2, 0),
    UNKNOWN("unknown", -1, -1, -1);

    /** Returns whether this version is equal to or newer than [other]. */
    fun isAtLeast(other: MinecraftVersion): Boolean = known && other.known && coordinates >= other.coordinates

    /** Returns whether this version is equal to or older than [other]. */
    fun isAtMost(other: MinecraftVersion): Boolean = known && other.known && coordinates <= other.coordinates

    /** Strict comparison: this version must be newer than [other]. */
    fun isNewerThan(other: MinecraftVersion): Boolean = known && other.known && coordinates > other.coordinates

    /** Strict comparison: this version must be older than [other]. */
    fun isOlderThan(other: MinecraftVersion): Boolean = known && other.known && coordinates < other.coordinates

    /** Readable alias for [isAtLeast]. */
    fun isSameOrNewerThan(other: MinecraftVersion): Boolean = isAtLeast(other)

    /** Readable alias for [isAtMost]. */
    fun isSameOrOlderThan(other: MinecraftVersion): Boolean = isAtMost(other)

    /**
     * Checks whether both versions belong to the same release line, ignoring
     * patch. For example, `1.20.4` and `1.20.6` share the `1.20` line.
     */
    fun isSameReleaseLine(other: MinecraftVersion): Boolean = known && other.known && major == other.major && minor == other.minor

    /**
     * Checks the inclusive range from [minimum] to [maximum].
     *
     * Java:
     * ```java
     * boolean legacy = version.isBetween(
     *     MinecraftVersion.V1_8_8,
     *     MinecraftVersion.V1_12_2
     * );
     * ```
     */
    fun isBetween(minimum: MinecraftVersion, maximum: MinecraftVersion): Boolean =
        isAtLeast(minimum) && isAtMost(maximum)

    /**
     * Java-friendly equivalent of the Kotlin expression `this in range`.
     * ```java
     * MinecraftVersionRange range = MinecraftVersionRange.atLeast(MinecraftVersion.V1_20_5);
     * if (version.inRange(range)) enableModernApi();
     * ```
     */
    fun inRange(range: MinecraftVersionRange): Boolean = this in range

    /**
     * Creates an inclusive range for `V1_8_8..V1_12_2` in Kotlin.
     */
    operator fun rangeTo(maximum: MinecraftVersion): MinecraftVersionRange = MinecraftVersionRange.between(this, maximum)

    /** `false` only for [UNKNOWN]. */
    val known: Boolean get() = this != UNKNOWN
    private val coordinates: Long get() = major * 1_000_000L + minor * 1_000L + patch

    companion object {
        private val byCoordinates = values().filter { it.known }.associateBy { Triple(it.major, it.minor, it.patch) }
        /**
         * Finds the first `number.number[.number]` version in [value].
         *
         * Supports strings such as `1.20.6-R0.1-SNAPSHOT`,
         * `git-Paper-123 (MC: 1.21.11)`, and `26.2-112-c9e894d`. Returns
         * [UNKNOWN] for empty, malformed, or not-yet-listed releases.
         */
        @JvmStatic fun parse(value: String?): MinecraftVersion {
            val match = Regex("(?<!\\d)(\\d+)\\.(\\d+)(?:\\.(\\d+))?").find(value.orEmpty()) ?: return UNKNOWN
            val coordinates = Triple(match.groupValues[1].toIntOrNull() ?: return UNKNOWN,
                match.groupValues[2].toIntOrNull() ?: return UNKNOWN,
                match.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0)
            return byCoordinates[coordinates] ?: UNKNOWN
        }
/**
         * Creates an inclusive range from [minimum] to [maximum].
         * ```java
         * MinecraftVersionRange legacy = MinecraftVersion.range(
         *     MinecraftVersion.V1_8_8,
         *     MinecraftVersion.V1_12_2
         * );
         * ```
         */
        @JvmStatic fun range(minimum: MinecraftVersion, maximum: MinecraftVersion): MinecraftVersionRange =
            MinecraftVersionRange.between(minimum, maximum)

        /** Creates `[minimum, +∞)`. */
        @JvmStatic fun atLeast(minimum: MinecraftVersion): MinecraftVersionRange = MinecraftVersionRange.atLeast(minimum)

        /** Creates `(-∞, maximum]`. */
        @JvmStatic fun atMost(maximum: MinecraftVersion): MinecraftVersionRange = MinecraftVersionRange.atMost(maximum)
    }
}
