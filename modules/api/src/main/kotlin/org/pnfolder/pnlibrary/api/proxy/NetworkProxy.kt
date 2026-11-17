package org.pnfolder.pnlibrary.api.proxy

import java.util.UUID

/**
 * The proxy server — platform-neutral access to players, backend servers,
 * plugin-message channels and lifecycle.
 *
 * Available on proxy runtimes (Velocity, BungeeCord) only. Obtain via:
 * ```kotlin
 * val proxy = library.proxy
 * ```
 */
interface NetworkProxy {
    val name: String
    val version: String
    val playerCount: Int

    val players: Collection<NetworkPlayer>
    val servers: List<NetworkServer>

    fun player(name: String): NetworkPlayer?
    fun player(uuid: UUID): NetworkPlayer?
    fun server(name: String): NetworkServer?

    fun registerChannel(channel: String)
    fun unregisterChannel(channel: String)

    /** Sends a text message to every player connected to this proxy. */
    fun broadcast(message: String)

    fun stop(reason: String? = null)
}
