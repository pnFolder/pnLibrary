package org.pnfolder.pnlibrary.bukkit.tasks;

import java.time.Duration;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender;
import org.pnfolder.pnlibrary.bukkit.compat.Folia;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest;
import org.pnfolder.pnlibrary.spi.tasks.RetirableCallback;

// Region schedulers refuse delay/period of zero for repeating work; both are raised to one tick (see ticks()).
final class FoliaSchedulerBridge {
    private final Plugin plugin;

    FoliaSchedulerBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    Object schedule(PlatformTaskRequest request) {
        Object scheduled;
        switch (request.getExecutionKind()) {
            case ASYNC:
                scheduled = scheduleAsync(request);
                break;
            case ENTITY:
                scheduled = scheduleOnPlayer(player(request.getTarget()), request);
                break;
            default:
                scheduled = scheduleGlobal(request);
        }
        if (scheduled == null) {
            throw new IllegalStateException("Folia did not schedule the task: the player has already left");
        }
        return scheduled;
    }

    void cancel(Object task) {
        Folia.cancel(task);
    }

    private Object scheduleAsync(PlatformTaskRequest request) {
        Duration interval = request.getInterval();
        if (interval != null) {
            return Folia.runAsyncRepeating(plugin, request.getDelay(), interval, request.getCallback());
        }
        if (request.getDelay().isZero()) {
            return Folia.runAsync(plugin, request.getCallback());
        }
        return Folia.runAsyncLater(plugin, request.getDelay(), request.getCallback());
    }

    private Object scheduleGlobal(PlatformTaskRequest request) {
        Duration interval = request.getInterval();
        if (interval != null) {
            return Folia.runGlobalRepeating(plugin, ticks(request.getDelay()), ticks(interval), request.getCallback());
        }
        if (request.getDelay().isZero()) {
            return Folia.runGlobal(plugin, request.getCallback());
        }
        return Folia.runGlobalLater(plugin, ticks(request.getDelay()), request.getCallback());
    }

    private Object scheduleOnPlayer(Player player, PlatformTaskRequest request) {
        Duration interval = request.getInterval();
        Runnable retired = () -> {
            if (request.getCallback() instanceof RetirableCallback) {
                ((RetirableCallback) request.getCallback()).retire();
            }
        };
        if (interval != null) {
            return Folia.runOnRepeating(player, plugin, ticks(request.getDelay()), ticks(interval),
                request.getCallback(), retired);
        }
        if (request.getDelay().isZero()) {
            return Folia.runOn(player, plugin, request.getCallback(), retired);
        }
        return Folia.runOnLater(player, plugin, ticks(request.getDelay()), request.getCallback(), retired);
    }

    private static long ticks(Duration value) {
        return Math.max(TaskTicks.durationToTicks(value), 1);
    }

    private static Player player(Object target) {
        Object native_ = target instanceof BukkitCommandSender ? ((BukkitCommandSender) target).getNative() : target;
        if (!(native_ instanceof Player)) {
            throw new IllegalArgumentException("A Folia entity task needs a Bukkit player as its target");
        }
        return (Player) native_;
    }
}
