package org.pnfolder.pnlibrary.spi.platform

import org.pnfolder.pnlibrary.api.platform.PlatformType
/** Stable identity information for a native platform adapter. */
interface PlatformIdentity {
    /** Platform family this adapter serves. */
    val type: PlatformType
    /** Stable platform identifier. */
    val id: String get() = type.id
    /** Human-readable platform name. */
    val implementationName: String get() = type.displayName
    /** Whether the platform is a proxy such as BungeeCord or Velocity. */
    val isProxy: Boolean get() = type.isProxy
    /** Whether the platform is a game server such as Bukkit. */
    val isServer: Boolean get() = type.isServer
    /** Stable suffix used by the installed distribution artifact. */
    val artifactId: String
        get() = when (type) {
            PlatformType.BUKKIT -> "bukkit"
            PlatformType.BUNGEECORD -> "bungee"
            PlatformType.VELOCITY -> "velocity"
            PlatformType.UNIVERSAL -> error("UNIVERSAL is a release artifact, not a plugin identity")
        }
    /** Native descriptor file used when validating downloaded plugin JARs. */
    val descriptorFileName: String
        get() = when (type) {
            PlatformType.BUKKIT -> "plugin.yml"
            PlatformType.BUNGEECORD -> "bungee.yml"
            PlatformType.VELOCITY -> "velocity-plugin.json"
            PlatformType.UNIVERSAL -> error("UNIVERSAL is a release artifact, not a plugin identity")
        }
}
