package org.pnfolder.pnlibrary.core.network;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.network.channel.Channels;
import org.pnfolder.pnlibrary.api.network.channel.MessageChannel;

/** The names of the channels open on a proxy or a game server, for the command and the menu. */
public final class ChannelNames {
    private ChannelNames() {
    }

    /** The open channel names, oldest first; empty when there is nothing to ask (no proxy, or no network on this side). */
    public static @NotNull List<String> of(@Nullable Channels<? extends MessageChannel> channels) {
        List<String> names = new ArrayList<>();
        if (channels != null) {
            for (MessageChannel channel : channels.channels()) {
                names.add(channel.key().asString());
            }
        }
        return names;
    }
}
