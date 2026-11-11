package org.pnfolder.pnlibrary.core.proxy

import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.commands.proxy.ProxyControlCommand
import org.pnfolder.pnlibrary.core.link.Link
import org.pnfolder.pnlibrary.core.link.LinkReceiver
import org.pnfolder.pnlibrary.core.link.LinkSecret
import org.pnfolder.pnlibrary.core.link.LinkTransport
import org.pnfolder.pnlibrary.core.link.NetworkPublisher
import org.pnfolder.pnlibrary.core.link.RemoteExecutor
import org.pnfolder.pnlibrary.core.menu.MenuDataServer
import org.pnfolder.pnlibrary.core.menu.OwnerInfo
import org.pnfolder.pnlibrary.core.menu.ProxyMenu
import java.nio.file.Path

/**
 * The proxy's side of the link, assembled once for every proxy platform: the link itself, `/pnproxy`, the menu data
 * game servers ask for, and the network map this proxy announces. A platform supplies only how bytes reach a game
 * server and how to describe a plugin.
 *
 * @param folder the pnLibrary folder, where `link.key` and `groups.tsv` live
 * @param warn receives a problem with `link.key`
 * @param describe the plugin behind a registration owner, or null
 * @param platformName the proxy software and its version, shown in the network map
 * @param players how many players are online on the whole proxy
 * @param broadcast sends the network map to every game server
 */
class ProxyLink(
    library: PnLibrary,
    folder: Path,
    warn: (String) -> Unit,
    transport: LinkTransport,
    describe: (Any?) -> OwnerInfo?,
    platformName: () -> String,
    players: () -> Int,
    private val broadcast: (ByteArray) -> Unit,
) : AutoCloseable {
    private val link = Link(LinkSecret.readOrCreate(folder, warn), folder, transport)
    private val control = ProxyControlCommand(library, link)
    private val data: MenuDataServer = ProxyMenu.server(library, link, control, describe)
    private val publisher = if (link.enabled) NetworkPublisher(library, link, platformName, players) else null

    /** Hand it the messages the platform receives from game servers. */
    val receiver = LinkReceiver(link, RemoteExecutor(library), data)

    /** Whether the network map is announced; false when there is no usable `link.key`. */
    val announces: Boolean get() = publisher != null

    init {
        control.register()
    }

    /** Sends the current network map to every game server. The platform calls it on [NETWORK_PERIOD_SECONDS]. */
    fun announce() {
        publisher?.let { broadcast(it.message()) }
    }

    override fun close() = data.close()

    companion object {
        const val NETWORK_DELAY_SECONDS = 10L
        const val NETWORK_PERIOD_SECONDS = 30L
    }
}
