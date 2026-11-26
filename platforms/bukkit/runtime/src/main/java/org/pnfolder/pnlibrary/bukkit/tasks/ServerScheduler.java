package org.pnfolder.pnlibrary.bukkit.tasks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.pnfolder.pnlibrary.bukkit.compat.Folia;
import org.pnfolder.pnlibrary.bukkit.compat.ServerCapabilities;

/**
 * Runs pnLibrary's own work on the thread that may do it.
 *
 * <p>On Bukkit and Paper that is the main thread. Folia has no main thread and its Bukkit scheduler throws on every
 * call, so there global work goes to the global region and work that touches a player goes to that player's region.
 * Delays are in server ticks.
 */
public final class ServerScheduler {
    private final Plugin plugin;

    public ServerScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Work that is waiting and can still be withdrawn. */
    @FunctionalInterface
    public interface Scheduled {
        /** Withdraws the work if it has not run yet. */
        void cancel();
    }

    /** Runs {@code task} at once when this thread may touch the world, otherwise on the next tick. */
    public void execute(Runnable task) {
        if (!ServerCapabilities.isFolia() && Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            nextTick(task);
        }
    }

    /** Runs {@code task} on the next tick, even when called from the main thread. */
    public void nextTick(Runnable task) {
        if (ServerCapabilities.isFolia()) {
            Folia.runGlobal(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Runs {@code task} once after {@code delayTicks}. Folia refuses a delay of zero ticks, so it is raised to one. */
    public Scheduled later(long delayTicks, Runnable task) {
        if (!ServerCapabilities.isFolia()) {
            BukkitTask native_ = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return native_::cancel;
        }
        return foliaTask(Folia.runGlobalLater(plugin, Math.max(delayTicks, 1), task));
    }

    /** Runs {@code task} every {@code periodTicks} after {@code delayTicks}; on Folia both are at least one tick. */
    public Scheduled repeat(long delayTicks, long periodTicks, Runnable task) {
        if (!ServerCapabilities.isFolia()) {
            BukkitTask native_ = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
            return native_::cancel;
        }
        return foliaTask(Folia.runGlobalRepeating(plugin, Math.max(delayTicks, 1), Math.max(periodTicks, 1), task));
    }

    /** Runs {@code task} off the server threads. */
    public void async(Runnable task) {
        if (ServerCapabilities.isFolia()) {
            Folia.runAsync(plugin, task);
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /** Whether this thread may touch {@code player} now. */
    public boolean owns(Player player) {
        return ServerCapabilities.isFolia() ? Folia.ownsRegionOf(player) : Bukkit.isPrimaryThread();
    }

    /**
     * Runs {@code task} at once when this thread owns {@code player}, otherwise on the player's thread.
     * On Folia nothing runs once the player has left.
     */
    public void onPlayer(Player player, Runnable task) {
        if (owns(player)) {
            task.run();
        } else if (ServerCapabilities.isFolia()) {
            Folia.runOn(player, plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Runs {@code task} on the thread of {@code player} after {@code delayTicks}. */
    public void onPlayerLater(Player player, long delayTicks, Runnable task) {
        if (ServerCapabilities.isFolia()) {
            Folia.runOnLater(player, plugin, Math.max(delayTicks, 1), task);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    private Scheduled foliaTask(Object native_) {
        return () -> {
            if (native_ != null) {
                Folia.cancel(native_);
            }
        };
    }
}
