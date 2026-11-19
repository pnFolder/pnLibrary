package org.pnfolder.pnlibrary.api.network

import java.util.UUID

/**
 * The proxy itself: its players, backend servers and plugin-message channels. Available on Velocity and BungeeCord
 * only; `library.proxy` is `null` on a game server.
 *
 * ```kotlin
 * val proxy = library.proxy ?: return
 * proxy.broadcast("Welcome to ${proxy.name}")
 * proxy.server("lobby")?.onlinePlayers
 * ```
 */
interface NetworkProxy {
    /** Proxy software name, such as `Velocity`. */
    val name: String

    /** Proxy software version. */
    val version: String

    /** Number of players connected to the proxy. */
    val playerCount: Int

    /** Every connected player. */
    val players: Collection<NetworkPlayer>

    /** Every registered backend server. */
    val servers: List<NetworkServer>

    /** Returns the connected player called [name], or `null`. */
    fun player(name: String): NetworkPlayer?

    /** Returns the connected player with [uuid], or `null`. */
    fun player(uuid: UUID): NetworkPlayer?

    /** Returns the backend server registered as [name], or `null`. */
    fun server(name: String): NetworkServer?

    /** Starts receiving plugin messages on [channel]. */
    fun registerChannel(channel: String)

    /** Stops receiving plugin messages on [channel]. */
    fun unregisterChannel(channel: String)

    /** Sends a text message to every player connected to this proxy. */
    fun broadcast(message: String)

    /** Shuts the proxy down, kicking players with [reason] when given. */
    fun stop(reason: String? = null)
}
