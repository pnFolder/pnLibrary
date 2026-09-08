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
) {
    BUKKIT("bukkit", "Bukkit", false),
    BUNGEECORD("bungeecord", "BungeeCord", true),
    VELOCITY("velocity", "Velocity", true),
    ;

    /** Whether this family runs a Minecraft server rather than a proxy. */
    val isServer: Boolean get() = !isProxy
}
