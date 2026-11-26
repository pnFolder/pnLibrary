package org.pnfolder.pnlibrary.velocity.link;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.link.LinkReceiver;
import org.pnfolder.pnlibrary.core.link.wire.LinkCodec;

/** Passes plugin messages on the pnLibrary link channel to the link receiver. */
public final class VelocityLinkListener {
    /** The link channel as Velocity names it. */
    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(LinkCodec.CHANNEL);

    private final LinkReceiver receiver;

    /** Creates a listener that forwards link frames to {@code receiver}. */
    public VelocityLinkListener(@NotNull LinkReceiver receiver) {
        this.receiver = receiver;
    }

    /** Handles one plugin message; frames on the link channel never reach a player or another server. */
    @Subscribe
    public void onMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        boolean fromServer = event.getSource() instanceof ServerConnection;
        String name = fromServer ? ((ServerConnection) event.getSource()).getServerInfo().getName() : "";
        receiver.receive(name, fromServer, event.getData());
    }
}
