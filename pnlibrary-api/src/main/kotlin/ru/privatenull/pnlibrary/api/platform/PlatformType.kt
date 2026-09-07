package ru.privatenull.pnlibrary.api.platform

/**
 * A runtime family supported by pnLibrary.
 *
 * This enum intentionally contains API families only. Concrete implementations
 * and forks are discovered at runtime by [PlatformAdapter.implementationName]
 * and never affect compatibility or distribution selection.
 *
 * @property id Stable lowercase identifier used in logs and diagnostics.
 * @property displayName Human-readable family name.
 * @property isProxy Whether this family runs as a Minecraft proxy.
 * @property distributionArtifact Distribution module used for updates.
 */
enum class PlatformType(
    val id: String,
    val displayName: String,
    val isProxy: Boolean,
    val distributionArtifact: String,
) {
    BUKKIT("bukkit", "Bukkit", false, "bukkit"),
    BUNGEECORD("bungeecord", "BungeeCord", true, "bungee"),
    VELOCITY("velocity", "Velocity", true, "velocity"),
    ;

    /** Whether this family runs a Minecraft server rather than a proxy. */
    val isServer: Boolean get() = !isProxy
}
