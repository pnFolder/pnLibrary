package org.pnfolder.pnlibrary.bungee

import net.md_5.bungee.api.connection.Server
import net.md_5.bungee.api.event.PluginMessageEvent
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.event.EventHandler
import org.pnfolder.pnlibrary.core.link.LinkCodec
import org.pnfolder.pnlibrary.core.link.LinkReceiver

/** Takes the reports that game servers send on the pnLibrary link channel; messages from players are dropped. */
internal class BungeeLinkListener(private val receiver: LinkReceiver) : Listener {
    @EventHandler
    fun onMessage(event: PluginMessageEvent) {
        if (event.tag != LinkCodec.CHANNEL) return
        // The channel is ours: nothing on it may reach a player or another server.
        event.isCancelled = true
        val sender = event.sender as? Server
        receiver.receive(sender?.info?.name.orEmpty(), sender != null, event.data)
    }
}
