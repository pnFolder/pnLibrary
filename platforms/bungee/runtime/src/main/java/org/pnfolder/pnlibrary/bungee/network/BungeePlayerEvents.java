package org.pnfolder.pnlibrary.bungee.network;

import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.events.player.PlayerLifecyclePublisher;

/** Publishes the portable player join and quit events from BungeeCord's native ones. */
public final class BungeePlayerEvents implements Listener {
    private final PnLibrary library;

    /** Creates a listener that publishes the events through {@code library}. */
    public BungeePlayerEvents(@NotNull PnLibrary library) {
        this.library = library;
    }

    /** Publishes the join event once the player is logged in. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPostLogin(PostLoginEvent event) {
        PlayerLifecyclePublisher.joined(library, event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    /** Publishes the quit event when the player leaves the proxy. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDisconnect(PlayerDisconnectEvent event) {
        PlayerLifecyclePublisher.left(library, event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
