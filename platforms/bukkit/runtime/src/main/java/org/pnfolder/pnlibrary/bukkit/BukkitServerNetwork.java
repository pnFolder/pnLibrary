package org.pnfolder.pnlibrary.bukkit;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import net.kyori.adventure.key.Key;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.network.channel.ServerChannel;
import org.pnfolder.pnlibrary.api.network.channel.ServerNetwork;
import org.pnfolder.pnlibrary.core.network.backend.BackendChannels;

/** {@link ServerNetwork} of a Bukkit server: the channels go through the server's messenger and any online player. */
final class BukkitServerNetwork implements ServerNetwork, PluginMessageListener, Listener, AutoCloseable {
    private final Plugin plugin;
    private final Messenger messenger;
    private final BackendChannels channels;

    BukkitServerNetwork(Plugin plugin, Supplier<PnLogger> logger) {
        this.plugin = plugin;
        this.messenger = plugin.getServer().getMessenger();
        this.channels = new BackendChannels(this::register, this::unregister, this::carry, (message, error) -> logger.get().warning(message, error));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @NotNull
    @Override
    public ServerChannel channel(@NotNull Key key) {
        return channels.channel(key);
    }

    @NotNull
    @Override
    public List<ServerChannel> channels() {
        return new ArrayList<ServerChannel>(channels.channels());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        channels.dispatch(channel, message);
    }

    /** A player has arrived, so the messages that waited can go. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        channels.flush();
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        channels.close();
    }

    private void register(Key key) {
        String name = key.asString();
        messenger.registerOutgoingPluginChannel(plugin, name);
        messenger.registerIncomingPluginChannel(plugin, name, this);
    }

    private void unregister(Key key) {
        String name = key.asString();
        messenger.unregisterOutgoingPluginChannel(plugin, name);
        messenger.unregisterIncomingPluginChannel(plugin, name);
    }

    private boolean carry(String channel, byte[] data) {
        for (Player carrier : Bukkit.getOnlinePlayers()) {
            carrier.sendPluginMessage(plugin, channel, data);
            return true;
        }
        return false;
    }
}
