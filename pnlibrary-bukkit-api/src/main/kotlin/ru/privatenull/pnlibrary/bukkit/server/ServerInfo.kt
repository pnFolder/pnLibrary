package ru.privatenull.pnlibrary.bukkit.server

import ru.privatenull.pnlibrary.bukkit.version.MinecraftVersion
import ru.privatenull.pnlibrary.bukkit.version.MinecraftVersionRange

/**
 * Read-only information about a Minecraft game server.
 *
 * This Bukkit-only model is intentionally absent from the cross-platform API.
 *
 * @property name server implementation name, for example `Paper` or `Purpur`.
 * @property version complete implementation version reported by the server.
 * @property minecraftVersion parsed, comparable Minecraft release.
 * @property rawMinecraftVersion unmodified Minecraft version string.
 */
data class ServerInfo(
    val name: String,
    val version: String,
    val minecraftVersion: MinecraftVersion,
    val rawMinecraftVersion: String,
) {
    /** Human-readable software name and version. */
    val displayName: String get() = if (version.isBlank()) name else "$name $version"

    /** Returns whether the server runs [minimum] or a newer known release. */
    fun isMinecraftAtLeast(minimum: MinecraftVersion): Boolean = minecraftVersion.isAtLeast(minimum)

    /** Returns whether the server Minecraft version belongs to [range]. */
    fun supports(range: MinecraftVersionRange): Boolean = minecraftVersion in range
}
