package org.pnfolder.pnlibrary.bungee.link;

import java.util.Objects;
import net.md_5.bungee.api.connection.Connection;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.link.LinkReceiver;
import org.pnfolder.pnlibrary.core.link.wire.LinkCodec;

/** Passes plugin messages on the pnLibrary link channel to the link receiver. */
public final class BungeeLinkListener implements Listener {
    private final LinkReceiver receiver;

    /** Creates a listener that forwards link frames to {@code receiver}. */
    public BungeeLinkListener(@NotNull LinkReceiver receiver) {
        this.receiver = Objects.requireNonNull(receiver, "receiver");
    }

    /** Handles one plugin message; frames on the link channel never reach a player or another server. */
    @EventHandler
    public void onMessage(PluginMessageEvent event) {
        if (!LinkCodec.CHANNEL.equals(event.getTag())) {
            return;
        }
        event.setCancelled(true);
        Connection sender = event.getSender();
        boolean fromServer = sender instanceof Server;
        String name = fromServer ? ((Server) sender).getInfo().getName() : "";
        receiver.receive(name, fromServer, event.getData());
    }
}
