package org.pnfolder.pnlibrary.api.remote

import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersionInfo

/**
 * Server and Minecraft protocol/version information exposed to a policy.
 *
 * @property version raw server-reported version string
 * @property minecraft parsed Minecraft version details
 */
data class ServerInfo(
    val version: String,
    val minecraft: MinecraftVersionInfo,
) {
    /** Parsed comparable Minecraft version. */
    val minecraftVersion: MinecraftVersion get() = minecraft.parsed
}
