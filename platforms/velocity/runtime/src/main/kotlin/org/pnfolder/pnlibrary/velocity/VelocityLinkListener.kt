package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import org.pnfolder.pnlibrary.core.link.LinkCodec
import org.pnfolder.pnlibrary.core.link.LinkReceiver

/** Takes the reports that game servers send on the pnLibrary link channel; messages from players are dropped. */
internal class VelocityLinkListener(private val receiver: LinkReceiver) {
    @Subscribe
    fun onMessage(event: PluginMessageEvent) {
        if (event.identifier != CHANNEL) return
        // The channel is ours: nothing on it may reach a player or another server.
        event.result = PluginMessageEvent.ForwardResult.handled()
        val source = event.source as? ServerConnection
        receiver.receive(source?.serverInfo?.name.orEmpty(), source != null, event.data)
    }

    companion object {
        /** The link channel as Velocity names it. */
        val CHANNEL: MinecraftChannelIdentifier = MinecraftChannelIdentifier.from(LinkCodec.CHANNEL)
    }
}
