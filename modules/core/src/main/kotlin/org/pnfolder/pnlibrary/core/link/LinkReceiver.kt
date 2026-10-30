package org.pnfolder.pnlibrary.core.link

/**
 * The proxy end of the link: takes a plugin message that arrived from a game server and remembers its report.
 *
 * The platform listener tells it whether the sender was a server connection or a player; a message from a player
 * is dropped before its signature is even looked at, and a message without the right signature is dropped too.
 *
 * @param key the shared key, or null when the proxy has none, which turns the link off
 */
class LinkReceiver(private val key: ByteArray?, private val registry: LinkRegistry = LinkHub.registry) {
    /** Whether the link can accept anything at all. */
    val enabled: Boolean get() = key != null

    /** Handles [message] from [server]; returns true when it was a valid report. */
    fun receive(server: String, fromServer: Boolean, message: ByteArray): Boolean {
        if (!fromServer || key == null) return false
        val report = LinkCodec.decode(message, key) ?: return false
        registry.accept(server, report)
        return true
    }
}
