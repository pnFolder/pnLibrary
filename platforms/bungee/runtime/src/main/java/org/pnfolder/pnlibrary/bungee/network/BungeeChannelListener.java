package org.pnfolder.pnlibrary.bungee.network;

import java.util.Objects;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.network.proxy.ProxyChannels;

/** Passes plugin messages on the channels opened through {@code NetworkProxy.channel} to their listeners. */
public final class BungeeChannelListener implements Listener {
    private final ProxyChannels channels;

    /** Creates a listener that feeds {@code channels}. */
    public BungeeChannelListener(@NotNull ProxyChannels channels) {
        this.channels = Objects.requireNonNull(channels, "channels");
    }

    /** Messages on an open channel never travel on; from a player they are dropped, as a client can forge them. */
    @EventHandler
    public void onMessage(PluginMessageEvent event) {
        String name = event.getTag();
        if (!channels.owns(name)) {
            return;
        }
        event.setCancelled(true);
        if (event.getSender() instanceof Server) {
            Server sender = (Server) event.getSender();
            channels.dispatch(name, new BungeeNetworkServer(sender.getInfo()), event.getData());
        }
    }
}
