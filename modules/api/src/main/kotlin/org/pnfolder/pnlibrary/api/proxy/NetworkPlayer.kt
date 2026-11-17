package org.pnfolder.pnlibrary.api.proxy

import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import java.net.InetSocketAddress

/**
 * A player connected through a proxy — extends the portable [LibraryPlayer] with
 * proxy-specific state: network address, ping, current backend server and disconnect.
 *
 * Obtain via [NetworkProxy.player] or [NetworkProxy.players].
 */
interface NetworkPlayer : LibraryPlayer {
    val address: InetSocketAddress?
    val ping: Long
    val currentServer: NetworkServer?
    fun disconnect(reason: String)
}
