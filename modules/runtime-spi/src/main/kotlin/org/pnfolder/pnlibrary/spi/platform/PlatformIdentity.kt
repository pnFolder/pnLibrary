package org.pnfolder.pnlibrary.spi.platform

import org.pnfolder.pnlibrary.api.platform.PlatformType
/** Stable identity information for a native platform adapter. */
interface PlatformIdentity {
    val type: PlatformType
    val id: String get() = type.id
    val implementationName: String get() = type.displayName
    val isProxy: Boolean get() = type.isProxy
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
