package org.pnfolder.pnlibrary.bukkit.inventory;

import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;

/**
 * Runs menu actions on the thread that owns the player: immediately when already there, otherwise scheduled on the
 * player's region (Folia) or the main thread.
 */
final class NativeMenuExecution implements MenuExecution {
    private final Plugin plugin;
    private final Supplier<PnLogger> pnLogger;
    private final ServerScheduler scheduler;

    NativeMenuExecution(Plugin plugin, Supplier<PnLogger> pnLogger) {
        this.plugin = plugin;
        this.pnLogger = pnLogger;
        this.scheduler = new ServerScheduler(plugin);
    }

    @Override
    public void requireOwner(Player player) {
        if (!scheduler.owns(player)) {
            throw new IllegalStateException(
                "Menu access requires the player's owning thread; schedule an entity task first");
        }
    }

    @Override
    public void execute(Player player, Runnable action) {
        if (scheduler.owns(player)) {
            action.run();
            return;
        }
        // A disabled plugin may not schedule anything; the server closes the views itself.
        if (!plugin.isEnabled()) {
            pnLogger.get().warning("Menu tracking was cleared during shutdown; native views require server/player cleanup");
            return;
        }
        scheduler.onPlayer(player, action);
    }
}
