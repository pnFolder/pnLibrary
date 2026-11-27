package org.pnfolder.pnlibrary.api.network.channel;

import org.pnfolder.pnlibrary.api.network.NetworkProxy;

/**
 * The game server's side of the network: its plugin-message channels to the proxy. Available on game servers only;
 * {@code library.getServerNetwork()} is {@code null} on a proxy, which uses {@link NetworkProxy}.
 */
public interface ServerNetwork extends Channels<ServerChannel> {
}
