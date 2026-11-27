package org.pnfolder.pnlibrary.core.network;

import org.pnfolder.pnlibrary.api.network.channel.MessageChannel;

/** A channel the registry can finish: it stops delivering and refuses new work. */
public interface ClosableChannel extends MessageChannel {
    /** Called once when the registry releases the channel. */
    void markClosed();
}
