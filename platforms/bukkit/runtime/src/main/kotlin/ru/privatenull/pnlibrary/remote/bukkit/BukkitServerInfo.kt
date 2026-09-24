package ru.privatenull.pnlibrary.remote.bukkit

import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersionInfo

class BukkitServerInfo internal constructor(
    val platform: BukkitPlatform,
    val platformName: String,
    val version: String,
    val minecraftVersionInfo: MinecraftVersionInfo,
) {
    val minecraftVersion: MinecraftVersion get() = minecraftVersionInfo.parsed
    val platformKey: String get() = platformName.filter { it.isLetterOrDigit() }.lowercase()
    fun isPlatform(expected: String?) = expected != null && platformKey == expected.filter { it.isLetterOrDigit() }.lowercase()
    fun isUnknownPlatform() = platform == BukkitPlatform.UNKNOWN
}
