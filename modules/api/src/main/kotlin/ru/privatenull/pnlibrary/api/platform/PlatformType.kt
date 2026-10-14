package ru.privatenull.pnlibrary.api.platform

/**
 * A runtime family supported by pnLibrary.
 *
 * This enum intentionally contains API families only. Concrete implementations
 * and forks are discovered by the installed runtime and never affect
 * compatibility or distribution selection.
 *
 * @property id Stable lowercase identifier used in logs and diagnostics.
 * @property displayName Human-readable family name.
 * @property isProxy Whether this family runs as a Minecraft proxy.
 */
enum class PlatformType(
    val id: String,
    val displayName: String,
    val isProxy: Boolean,
    /** Stable suffix used by published pnLibrary distribution artifacts. */
    val artifactId: String,
    /** Native plugin descriptor file used when validating downloaded plugin JARs. */
    val descriptorFileName: String,
) {
    /** Bukkit-compatible server family, including Paper, Spigot, and Folia-compatible runtimes. */
    BUKKIT("bukkit", "Bukkit", false, "bukkit", "plugin.yml"),

    /** BungeeCord-compatible proxy family, including compatible forks such as Waterfall. */
    BUNGEECORD("bungeecord", "BungeeCord", true, "bungee", "bungee.yml"),

    /** Velocity proxy family using its native scheduler, commands, and plugin container model. */
    VELOCITY("velocity", "Velocity", true, "velocity", "velocity-plugin.json"),

    ;

    /** Whether this family runs a Minecraft server rather than a proxy. */
    val isServer: Boolean get() = !isProxy
}
