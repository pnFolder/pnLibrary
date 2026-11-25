package org.pnfolder.pnlibrary.api.runtime.group

import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.jetbrains.annotations.ApiStatus

import org.pnfolder.pnlibrary.api.network.NetworkProxy
import org.pnfolder.pnlibrary.api.platform.PlatformProvider

/**
 * Which library is running and where: its version, settings, platform and proxy.
 *
 * ```kotlin
 * if (!PnLibraryProvider.get().runtime.isAtLeastVersion("0.1.0")) return
 * ```
 */
@ApiStatus.NonExtendable
interface RuntimeInfo {
    /** Version of the installed runtime. */
    val version: String

    /** Returns `true` when this runtime is not older than [minimumVersion]. */
    fun isAtLeastVersion(minimumVersion: String): Boolean

    /** Native platform plugin instance that owns this runtime. */
    val owner: Any

    /** Validated configuration of the installed runtime. */
    val configuration: PnLibraryConfig

    /** Type-safe native API for the active Bukkit, Bungee, or Velocity runtime. */
    val platforms: PlatformProvider

    /** The proxy layer; null when running on a game server instead of a proxy. */
    val proxy: NetworkProxy?

    /** Whether the runtime has begun closing and no longer accepts normal work. */
    val isClosed: Boolean
}
