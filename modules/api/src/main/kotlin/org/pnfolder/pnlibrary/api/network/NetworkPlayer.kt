package org.pnfolder.pnlibrary.api.network

import net.kyori.adventure.text.Component
import java.time.Duration
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer
import java.net.InetSocketAddress

/**
 * A player connected through a proxy: a [OnlinePlayer] plus what only a proxy knows, such as the backend server
 * the player is on.
 *
 * ```kotlin
 * val player = library.proxy?.player("Steve")
 * player?.currentServer?.name   // "lobby"
 * player?.disconnect(Component.text("Maintenance"))
 * ```
 *
 * Obtain one from [NetworkProxy.player] or [NetworkProxy.players].
 */
interface NetworkPlayer : OnlinePlayer {
    /** Remote address of the client, or `null` when the platform does not expose it. */
    val address: InetSocketAddress?

    /** Last measured latency between the proxy and the client. */
    val ping: Duration

    /** The backend server the player is on, or `null` while connecting. */
    val currentServer: NetworkServer?

    /** Disconnects the player and shows [reason] on the disconnect screen. */
    fun disconnect(reason: Component)

    /** Disconnects the player with an empty disconnect screen. */
    fun disconnect() = disconnect(Component.empty())
}
