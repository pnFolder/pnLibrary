package org.pnfolder.pnlibrary.api.compat.declaration

import org.pnfolder.pnlibrary.api.compat.MinecraftSupport
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersionInfo

/**
 * The Minecraft versions a plugin or module runs on. Build it with [from], [between], [upTo] or [only], or take a ready
 * one ([ANY], [LEGACY], [MODERN]), and join several with `+`: the server must fit at least one part.
 *
 * Versions are compared by their numbers, never by a list of known releases, so a release pnLibrary has not heard of yet
 * still compares correctly: `from("1.16.5")` accepts it. Give a version as a [MinecraftVersion] constant or as text, for a
 * release that has no constant yet.
 *
 * ```kotlin
 * val versions = MinecraftVersions.LEGACY + MinecraftVersions.from("1.16.5")
 * setup.supportedMinecraft(versions)
 * if (versions.accepts("1.14.4")) ... // false: 1.14.4 is between the two parts
 * ```
 *
 * @property ranges the parts, each from a version to a version; a `null` side is open. Empty means nothing was declared.
 */
class MinecraftVersions private constructor(val ranges: List<Range>) {
    /** One part: [minimum] to [maximum] included, written as text such as `1.16.5`; a `null` side is open. */
    data class Range(val minimum: String?, val maximum: String?)

    /** Whether any version was declared. */
    val isDeclared: Boolean get() = ranges.isNotEmpty()

    /** The declared versions in words, such as `1.16.5-1.21.4, 1.8.8`, or `null` when none. */
    val description: String?
        get() = ranges.takeIf { it.isNotEmpty() }?.joinToString(", ") { range ->
            val (low, high) = range.minimum to range.maximum
            when {
                low != null && high != null && number(low) == number(high) -> low
                low != null && high != null -> "$low-$high"
                low != null -> "$low+"
                high != null -> "до $high"
                else -> "любая"
            }
        }

    /** These versions and [other] together. */
    operator fun plus(other: MinecraftVersions): MinecraftVersions = MinecraftVersions(ranges + other.ranges)

    /** Whether the server's [version] is inside the declared versions, or nothing was declared. */
    fun accepts(version: MinecraftVersion): Boolean = accepts(version.text)

    /** Whether the server's [version], written as text, is inside the declared versions, or nothing was declared. */
    fun accepts(version: String): Boolean = check(MinecraftVersion.parseInfo(version)).let { it == MinecraftSupport.SUPPORTED || it == MinecraftSupport.NOT_DECLARED }

    /** Where the server's [version] stands; [MinecraftSupport.UNKNOWN] when it has no numbers. */
    fun check(version: MinecraftVersionInfo?): MinecraftSupport {
        if (ranges.isEmpty()) return MinecraftSupport.NOT_DECLARED
        if (version == null || !version.numeric) return MinecraftSupport.UNKNOWN
        val at = Triple(version.major!!, version.minor!!, version.patch ?: 0)
        if (ranges.any { fits(at, it) }) return MinecraftSupport.SUPPORTED
        val highest = if (ranges.any { it.maximum == null }) null else ranges.map { number(it.maximum!!) }.maxWithOrNull { a, b -> compare(a, b) }
        val lowest = if (ranges.any { it.minimum == null }) null else ranges.map { number(it.minimum!!) }.minWithOrNull { a, b -> compare(a, b) }
        return when {
            highest != null && compare(at, highest) > 0 -> MinecraftSupport.NEWER_THAN_DECLARED
            lowest != null && compare(at, lowest) < 0 -> MinecraftSupport.OLDER_THAN_DECLARED
            else -> MinecraftSupport.EXCLUDED
        }
    }

    override fun equals(other: Any?): Boolean = other is MinecraftVersions && other.ranges == ranges

    override fun hashCode(): Int = ranges.hashCode()

    override fun toString(): String = description ?: "not declared"

    private fun fits(at: Triple<Int, Int, Int>, range: Range) =
        (range.minimum == null || compare(at, number(range.minimum)) >= 0) && (range.maximum == null || compare(at, number(range.maximum)) <= 0)

    companion object {
        /** Nothing declared: every version is fine, and a module takes its plugin's declaration. */
        @JvmField
        val NONE = MinecraftVersions(emptyList())

        /** Every version, declared on purpose. */
        @JvmField
        val ANY = MinecraftVersions(listOf(Range(null, null)))

        /** The versions before the 1.13 flattening: 1.12.2 and older. */
        @JvmField
        val LEGACY = upTo(MinecraftVersion.V1_12_2)

        /** The versions since the 1.13 flattening: 1.13 and newer, also releases pnLibrary does not know yet. */
        @JvmField
        val MODERN = from(MinecraftVersion.V1_13)

        /** [version] and every newer one, also releases pnLibrary does not know yet. */
        @JvmStatic
        fun from(version: MinecraftVersion): MinecraftVersions = from(text(version))

        /** [version], written as text, and every newer one. */
        @JvmStatic
        fun from(version: String): MinecraftVersions = between(version, null)

        /** Every version up to [version], included. */
        @JvmStatic
        fun upTo(version: MinecraftVersion): MinecraftVersions = upTo(text(version))

        /** Every version up to [version], written as text, included. */
        @JvmStatic
        fun upTo(version: String): MinecraftVersions = between(null, version)

        /** The versions from [from] to [to], both included. */
        @JvmStatic
        fun between(from: MinecraftVersion, to: MinecraftVersion): MinecraftVersions = between(text(from), text(to))

        /** The versions from [from] to [to], written as text, both included; a `null` side is open. */
        @JvmStatic
        fun between(from: String?, to: String?): MinecraftVersions {
            val low = from?.let(::number)
            val high = to?.let(::number)
            require(low == null || high == null || compare(low, high) <= 0) { "The oldest version $from is newer than the newest $to" }
            return MinecraftVersions(listOf(Range(from, to)))
        }

        /** Exactly these versions and nothing between them. */
        @JvmStatic
        fun only(vararg versions: MinecraftVersion): MinecraftVersions = only(*versions.map(::text).toTypedArray())

        /** Exactly these versions, written as text, and nothing between them. */
        @JvmStatic
        fun only(vararg versions: String): MinecraftVersions {
            require(versions.isNotEmpty()) { "Give at least one version" }
            return versions.map { between(it, it) }.reduce(MinecraftVersions::plus)
        }

        private fun text(version: MinecraftVersion): String {
            require(version != MinecraftVersion.UNKNOWN) { "UNKNOWN is not a Minecraft version; give the version as text" }
            return version.text
        }

        private fun number(text: String): Triple<Int, Int, Int> {
            val info = MinecraftVersion.parseInfo(text)
            require(info.numeric) { "Not a Minecraft version: $text" }
            return Triple(info.major!!, info.minor!!, info.patch ?: 0)
        }

        private fun compare(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int =
            compareValuesBy(a, b, { it.first }, { it.second }, { it.third })
    }
}
