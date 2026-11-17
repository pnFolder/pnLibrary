package org.pnfolder.pnlibrary.api.proxy

import java.net.InetSocketAddress

/**
 * A backend game server registered behind the proxy.
 *
 * Platform defaults: [motd] is null and [isRestricted] is false where the platform
 * does not expose them (Velocity). [ping] is null until at least one measurement is available.
 */
interface NetworkServer {
    val name: String
    val address: InetSocketAddress
    val onlinePlayers: Int

    /** Last measured round-trip time to this server in milliseconds; null when not yet known. */
    val ping: Long?

    /** BungeeCord server MOTD; null on platforms that do not expose it. */
    val motd: String? get() = null

    /** Whether the server rejects players without explicit permission. False on platforms that do not expose it. */
    val isRestricted: Boolean get() = false

    /** Sends plugin-message [bytes] to this server over [channel]; false when the server cannot be reached. */
    fun send(channel: String, bytes: ByteArray): Boolean

    /** Whether [player] is allowed to connect to this server. True on platforms without an access list. */
    fun canAccess(player: NetworkPlayer): Boolean = true

    /** Sends a text message to every player currently on this server. */
    fun broadcast(message: String)
}
