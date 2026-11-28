package org.pnfolder.pnlibrary.api.network.channel.typed;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.network.NetworkServer;
import org.pnfolder.pnlibrary.api.network.channel.NetworkChannel;
import org.pnfolder.pnlibrary.api.network.message.MessageReader;

/** Receives the messages of one type that a backend server sent on a {@link NetworkChannel}. */
@FunctionalInterface
public interface MessageListener {
    /** Called on the proxy's event thread; read {@code message} in the order the sender wrote it. */
    void onMessage(@NotNull NetworkServer from, @NotNull MessageReader message);
}
