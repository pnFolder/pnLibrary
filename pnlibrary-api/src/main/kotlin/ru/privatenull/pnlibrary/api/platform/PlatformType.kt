package ru.privatenull.pnlibrary.api.platform

/**
 * A runtime family supported by pnLibrary.
 *
 * Forks such as Paper, Purpur, Leaf, Folia, and Waterfall are represented by
 * [PlatformVariant]. Keeping the family separate makes capability checks and
 * distribution selection independent of a particular server implementation.
 *
 * @property displayName Human-readable family name.
 * @property isProxy Whether this family runs as a Minecraft proxy.
 * @property distributionArtifact Distribution module used for updates.
 */
enum class PlatformType(
    val displayName: String,
    val isProxy: Boolean,
    val distributionArtifact: String,
) {
    BUKKIT("Bukkit", false, "bukkit"),
    BUNGEECORD("BungeeCord", true, "bungee"),
    VELOCITY("Velocity", true, "velocity"),
    ;

    /** Whether this family runs a Minecraft server rather than a proxy. */
    val isServer: Boolean get() = !isProxy
}
