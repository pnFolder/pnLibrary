package org.pnfolder.pnlibrary.api.network.channel;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.Keyed;
import org.jetbrains.annotations.NotNull;

/** What every plugin-message channel has in common, on a proxy and on a game server. */
public interface MessageChannel extends Keyed {
    /** The longest message the server software carries, in bytes; a longer one is refused with an exception. */
    int MAX_MESSAGE_BYTES = 32766;

    /** The channel key: namespace and name, for example {@code myplugin:main}. */
    @NotNull
    @Override
    Key key();

    /** Whether the channel is still registered. */
    boolean isOpen();

    /** Unregisters the channel and drops its listeners. Opening the same name again gives a new channel. */
    void close();
}
