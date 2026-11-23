package org.pnfolder.pnlibrary.api.network

import org.pnfolder.pnlibrary.api.player.LibraryPlayer
import java.net.InetSocketAddress

/**
 * A player connected through a proxy: a [LibraryPlayer] plus what only a proxy knows, such as the backend server
 * the player is on.
 *
 * ```kotlin
 * val player = library.proxy?.player("Steve")
 * player?.currentServer?.name   // "lobby"
 * player?.disconnect("Maintenance")
 * ```
 *
 * Obtain one from [NetworkProxy.player] or [NetworkProxy.players].
 */
interface NetworkPlayer : LibraryPlayer {
    /** Remote address of the client, or `null` when the platform does not expose it. */
    val address: InetSocketAddress?

    /** Last measured latency between the proxy and the client, in milliseconds. */
    val ping: Long

    /** The backend server the player is on, or `null` while connecting. */
    val currentServer: NetworkServer?

    /** Disconnects the player and shows [reason] on the disconnect screen. */
    fun disconnect(reason: String)
}
