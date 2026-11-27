package org.pnfolder.pnlibrary.core.network;

import org.pnfolder.pnlibrary.api.network.channel.MessageChannel;

/** The size limit of a plugin message. */
public final class MessageSize {
    private MessageSize() {
    }

    public static void check(String channel, byte[] data) {
        if (data.length > MessageChannel.MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("A message of " + data.length + " bytes is too long for channel " + channel
                    + ": the limit is " + MessageChannel.MAX_MESSAGE_BYTES + " bytes");
        }
    }
}
