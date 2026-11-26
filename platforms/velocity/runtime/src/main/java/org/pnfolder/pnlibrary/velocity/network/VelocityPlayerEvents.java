package org.pnfolder.pnlibrary.velocity.network;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.events.player.PlayerLifecyclePublisher;

/** Publishes the portable player join and quit events from Velocity's native ones. */
public final class VelocityPlayerEvents {
    private final PnLibrary library;

    /** Creates a listener that publishes the events through {@code library}. */
    public VelocityPlayerEvents(@NotNull PnLibrary library) {
        this.library = library;
    }

    /** Publishes the portable join event once the player is logged in. */
    @Subscribe(order = PostOrder.LAST)
    public void onPostLogin(PostLoginEvent event) {
        PlayerLifecyclePublisher.joined(library, event.getPlayer().getUniqueId(),
                event.getPlayer().getUsername());
    }

    /** Publishes the portable quit event when the player leaves the proxy. */
    @Subscribe(order = PostOrder.LAST)
    public void onDisconnect(DisconnectEvent event) {
        PlayerLifecyclePublisher.left(library, event.getPlayer().getUniqueId(),
                event.getPlayer().getUsername());
    }
}
