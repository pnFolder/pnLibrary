package ru.privatenull.pnlibrary.api.platform

/**
 * The concrete server or proxy implementation detected at runtime.
 *
 * A variant always belongs to one of the three supported [PlatformType]
 * families. Use [type] for behavior and dependency selection; use [id] or
 * [displayName] only when the concrete implementation must be identified.
 *
 * @property type Base platform family implemented by this variant.
 * @property id Stable lowercase identifier used in logs and diagnostics.
 * @property displayName Human-readable implementation name.
 */
enum class PlatformVariant(
    val type: PlatformType,
    val id: String,
    val displayName: String,
) {
    BUKKIT(PlatformType.BUKKIT, "bukkit", "Bukkit / Spigot"),
    PAPER(PlatformType.BUKKIT, "paper", "Paper"),
    PURPUR(PlatformType.BUKKIT, "purpur", "Purpur"),
    LEAF(PlatformType.BUKKIT, "leaf", "Leaf"),
    FOLIA(PlatformType.BUKKIT, "folia-bukkit", "Folia"),
    BUNGEECORD(PlatformType.BUNGEECORD, "bungeecord", "BungeeCord"),
    WATERFALL(PlatformType.BUNGEECORD, "waterfall", "Waterfall"),
    NULLCORDX(PlatformType.BUNGEECORD, "nullcordx", "NullCordX"),
    VELOCITY(PlatformType.VELOCITY, "velocity", "Velocity"),
}
