package org.pnfolder.pnlibrary.core.link.remote;

import org.jetbrains.annotations.NotNull;

/** Sends bytes to a game server by name from the proxy. */
public interface LinkTransport {
    /** Sends {@code message} to {@code server}; false when it cannot be reached. */
    boolean send(@NotNull String server, @NotNull byte[] message);
}
