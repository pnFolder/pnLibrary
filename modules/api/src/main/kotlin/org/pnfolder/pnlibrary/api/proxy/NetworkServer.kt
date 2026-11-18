package org.pnfolder.pnlibrary.api.proxy

import java.net.InetSocketAddress

/**
 * A backend game server registered behind the proxy.
 *
 * ```kotlin
 * val lobby = library.proxy?.server("lobby")
 * lobby?.broadcast("Restart in 5 minutes")
 * lobby?.send("myplugin:sync", payload)
 * ```
 *
 * Properties the platform does not expose have neutral defaults: [motd] is `null` and [isRestricted] is `false`
 * on Velocity.
 */
interface NetworkServer {
    /** Name of the server as registered in the proxy configuration. */
    val name: String

    /** Address the proxy connects to. */
    val address: InetSocketAddress

    /** Number of players currently on this server. */
    val onlinePlayers: Int

    /** Last measured round-trip time to this server in milliseconds; `null` until the first measurement. */
    val ping: Long?

    /** Server MOTD; `null` on platforms that do not expose it. */
    val motd: String? get() = null

    /** Whether the server rejects players without explicit permission; `false` on platforms without that setting. */
    val isRestricted: Boolean get() = false

    /** Sends plugin-message [bytes] to this server over [channel]; `false` when the server cannot be reached. */
    fun send(channel: String, bytes: ByteArray): Boolean

    /** Whether [player] may connect to this server; `true` on platforms without an access list. */
    fun canAccess(player: NetworkPlayer): Boolean = true

    /** Sends a text message to every player currently on this server. */
    fun broadcast(message: String)
}
