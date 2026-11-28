package org.pnfolder.pnlibrary.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup;
import org.pnfolder.pnlibrary.velocity.network.VelocityNetworkProxy;
import org.pnfolder.pnlibrary.velocity.network.VelocityPlayerEvents;

/** What the library registers with Velocity's event manager: player events and plugin-message channels. */
final class VelocityListeners implements AutoCloseable {
    private final ProxyServer server;
    private final Object owner;
    private final VelocityPlayerEvents playerEvents;
    private final AutoCloseable channels;

    private VelocityListeners(ProxyServer server, Object owner, VelocityPlayerEvents playerEvents, AutoCloseable channels) {
        this.server = server;
        this.owner = owner;
        this.playerEvents = playerEvents;
        this.channels = channels;
    }

    static @NotNull VelocityListeners register(
            Object owner, ProxyServer server, PnLibrary library, VelocityNetworkProxy network) {
        VelocityPlayerEvents playerEvents = new VelocityPlayerEvents(library);
        server.getEventManager().register(owner, playerEvents);
        return new VelocityListeners(server, owner, playerEvents, network.listenFor(owner));
    }

    /** Takes every listener back; a failing step does not stop the others. */
    @SuppressWarnings("unchecked")
    @Override
    public void close() {
        ResourceCleanup.closeAll(
                () -> server.getEventManager().unregisterListener(owner, playerEvents),
                channels::close);
    }
}
