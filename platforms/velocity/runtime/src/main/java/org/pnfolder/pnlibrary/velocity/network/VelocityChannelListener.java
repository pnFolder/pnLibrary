package org.pnfolder.pnlibrary.velocity.network;

import java.util.Objects;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.network.proxy.ProxyChannels;

/** Passes plugin messages on the channels opened through {@code NetworkProxy.channel} to their listeners. */
public final class VelocityChannelListener {
    private final ProxyChannels channels;

    /** Creates a listener that feeds {@code channels}. */
    public VelocityChannelListener(@NotNull ProxyChannels channels) {
        this.channels = Objects.requireNonNull(channels, "channels");
    }

    /** Messages on an open channel never travel on; from a player they are dropped, as a client can forge them. */
    @Subscribe
    public void onMessage(PluginMessageEvent event) {
        String name = event.getIdentifier().getId();
        if (!channels.owns(name)) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (event.getSource() instanceof ServerConnection) {
            ServerConnection source = (ServerConnection) event.getSource();
            channels.dispatch(name, new VelocityNetworkServer(source.getServer()), event.getData());
        }
    }
}
