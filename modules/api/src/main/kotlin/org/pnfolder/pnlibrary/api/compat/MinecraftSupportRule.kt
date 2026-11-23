package org.pnfolder.pnlibrary.api.compat

import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersionInfo

/**
 * The Minecraft versions a plugin or module runs on, and what happens outside them.
 *
 * Versions are compared by their numbers (`major.minor.patch`), never by a list of known releases, so a release pnLibrary has
 * not heard of yet still compares correctly: `from("1.16.5")` accepts it, an upper limit puts it in [MinecraftSupport.NEWER_THAN_DECLARED].
 * Several declarations add up: the server must fit at least one.
 */
class MinecraftSupportRule {
    private class Segment(val minimum: Triple<Int, Int, Int>?, val maximum: Triple<Int, Int, Int>?, val text: String)

    private val segments = mutableListOf<Segment>()

    /** What to do outside the declared versions, or `null` to take the plugin's choice, then [UnsupportedAction.DISABLE_MODULE]. */
    var unsupportedAction: UnsupportedAction? = null

    /** What to do above the newest declared version, or `null` to take the plugin's choice, then [NewerAction.ALLOW_WITH_WARNING]. */
    var newerAction: NewerAction? = null

    /** Whether any version was declared. */
    val isDeclared: Boolean @Synchronized get() = segments.isNotEmpty()

    /** The declared versions in words, such as `1.16.5-1.21.4, 1.8.8`, or `null` when none. */
    val description: String? @Synchronized get() = segments.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.text }

    /** Adds the versions from [minimum] to [maximum], both included; a `null` side is open. */
    @Synchronized
    fun between(minimum: String?, maximum: String?) {
        val low = minimum?.let(::parse)
        val high = maximum?.let(::parse)
        require(low == null || high == null || compare(low, high) <= 0) { "The oldest version $minimum is newer than the newest $maximum" }
        val text = when {
            low != null && high != null && low == high -> minimum!!
            low != null && high != null -> "$minimum-$maximum"
            low != null -> "$minimum+"
            high != null -> "до $maximum"
            else -> "любая"
        }
        segments += Segment(low, high, text)
    }

    /** Where the server's [version] stands; [MinecraftSupport.UNKNOWN] when it has no numbers. */
    @Synchronized
    fun evaluate(version: MinecraftVersionInfo?): MinecraftSupport {
        if (segments.isEmpty()) return MinecraftSupport.NOT_DECLARED
        if (version == null || !version.numeric) return MinecraftSupport.UNKNOWN
        val at = Triple(version.major!!, version.minor!!, version.patch ?: 0)
        if (segments.any { (it.minimum == null || compare(at, it.minimum) >= 0) && (it.maximum == null || compare(at, it.maximum) <= 0) }) {
            return MinecraftSupport.SUPPORTED
        }
        val highest = if (segments.any { it.maximum == null }) null else segments.maxWithOrNull { a, b -> compare(a.maximum!!, b.maximum!!) }?.maximum
        val lowest = if (segments.any { it.minimum == null }) null else segments.minWithOrNull { a, b -> compare(a.minimum!!, b.minimum!!) }?.minimum
        return when {
            highest != null && compare(at, highest) > 0 -> MinecraftSupport.NEWER_THAN_DECLARED
            lowest != null && compare(at, lowest) < 0 -> MinecraftSupport.OLDER_THAN_DECLARED
            else -> MinecraftSupport.EXCLUDED
        }
    }

    private fun parse(text: String): Triple<Int, Int, Int> {
        val info = MinecraftVersion.parseInfo(text)
        require(info.numeric) { "Not a Minecraft version: $text" }
        return Triple(info.major!!, info.minor!!, info.patch ?: 0)
    }

    private fun compare(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int =
        compareValuesBy(a, b, { it.first }, { it.second }, { it.third })
}
