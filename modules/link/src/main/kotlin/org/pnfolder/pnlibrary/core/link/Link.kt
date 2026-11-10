package org.pnfolder.pnlibrary.core.link

import java.nio.file.Path
import java.time.Instant

/**
 * This server's side of the link between the proxy and its game servers.
 *
 * The platform runtime makes one when it starts and hands it to the commands, menus and listeners that talk to other
 * servers. A link without [key] is off: nothing is sent and nothing received.
 *
 * @param key the shared `link.key`, or null when the file is missing
 * @param folder the pnLibrary folder of the proxy, where `groups.tsv` lives; null on a game server
 * @param transport on the proxy: sends to a game server by name
 * @param uplink on a game server: sends to the proxy through any player online, false when nobody is online
 */
class Link(
    val key: ByteArray?,
    private val folder: Path? = null,
    private val transport: LinkTransport? = null,
    private val uplink: ((ByteArray) -> Boolean)? = null,
) {
    /** Whether the link has a key; without one nothing is sent or received. */
    val enabled: Boolean get() = key != null
    /** Whether this link is the proxy's end, which can send to servers by name. */
    val onProxy: Boolean get() = transport != null

    /** On the proxy: the game servers heard from since it started. */
    val registry = LinkRegistry()
    /** The remote requests this end made and has no answer to yet. */
    val replies = RemoteReplies()
    /** The data questions this end asked and has no answer to yet. */
    val data = DataClient(key) { bytes -> uplink?.invoke(bytes) ?: false }
    /** Sends update controls to other servers. */
    val control = RemoteControl(this)

    /** On a game server: the latest network map from the proxy, or null until one arrives. */
    @Volatile
    var network: ReceivedNetwork? = null
        private set

    /** On a game server: what answers the menu questions of other servers, once the menu is ready. */
    @Volatile
    var calls: DataHandler? = null
        private set

    /** Keeps the latest network map from the proxy, as received at [now]. */
    fun remember(report: NetworkReport, now: Instant = Instant.now()) {
        network = ReceivedNetwork(report, now)
    }

    /** Sets what answers the data questions other servers ask of this one. */
    fun answerCallsWith(handler: DataHandler) {
        calls = handler
    }

    private val groups = ServerGroups { folder?.resolve(ServerGroups.FILE) }

    /** The groups of the proxy; one instance, so its changes do not overwrite each other. */
    fun groups(): ServerGroups = groups

    /** Sends [message] to [target] from the proxy, or to the proxy from a game server. */
    fun send(target: String, message: ByteArray): Boolean =
        transport?.send(target, message) ?: uplink?.invoke(message) ?: false

    /** A network map and when this server received it. */
    data class ReceivedNetwork(val report: NetworkReport, val receivedAt: Instant)

    companion object {
        /** A link that is switched off, for places that run without one. */
        val OFF = Link(null)
    }
}
