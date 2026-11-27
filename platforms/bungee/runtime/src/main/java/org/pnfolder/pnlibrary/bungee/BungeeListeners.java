package org.pnfolder.pnlibrary.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.bungee.network.BungeeNetworkProxy;
import org.pnfolder.pnlibrary.bungee.network.BungeePlayerEvents;
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup;

/** What the library registers with BungeeCord's plugin manager: player events and plugin-message channels. */
final class BungeeListeners implements AutoCloseable {
    private final Plugin plugin;
    private final BungeePlayerEvents playerEvents;
    private final AutoCloseable channels;

    private BungeeListeners(Plugin plugin, BungeePlayerEvents playerEvents, AutoCloseable channels) {
        this.plugin = plugin;
        this.playerEvents = playerEvents;
        this.channels = channels;
    }

    static @NotNull BungeeListeners register(Plugin plugin, PnLibrary library, BungeeNetworkProxy network) {
        BungeePlayerEvents playerEvents = new BungeePlayerEvents(library);
        plugin.getProxy().getPluginManager().registerListener(plugin, playerEvents);
        return new BungeeListeners(plugin, playerEvents, network.listenFor(plugin));
    }

    /** Takes every listener back; a failing step does not stop the others. */
    @SuppressWarnings("unchecked")
    @Override
    public void close() {
        ResourceCleanup.closeAll(
                () -> plugin.getProxy().getPluginManager().unregisterListener(playerEvents),
                channels::close);
    }
}
