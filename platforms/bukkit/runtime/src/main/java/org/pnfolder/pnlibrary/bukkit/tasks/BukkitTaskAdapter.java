package org.pnfolder.pnlibrary.bukkit.tasks;

import java.time.Duration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.bukkit.compat.ServerCapabilities;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest;
import org.pnfolder.pnlibrary.spi.tasks.TrackingPlatformTaskAdapter;

/** Schedules portable tasks on the Bukkit scheduler, or on the Folia region schedulers. */
public final class BukkitTaskAdapter extends TrackingPlatformTaskAdapter {
    private final Plugin plugin;
    private volatile FoliaSchedulerBridge foliaScheduler;

    public BukkitTaskAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Rounds a non-zero duration up to the next server tick. */
    public static long durationToTicks(Duration value) {
        return TaskTicks.durationToTicks(value);
    }

    @Override
    protected PlatformTaskHandle scheduleNative(PlatformTaskRequest request) {
        Object native_ = ServerCapabilities.isFolia() ? folia().schedule(request) : scheduleBukkit(request);
        return () -> {
            if (native_ instanceof BukkitTask) {
                ((BukkitTask) native_).cancel();
            } else {
                folia().cancel(native_);
            }
            return true;
        };
    }

    private FoliaSchedulerBridge folia() {
        FoliaSchedulerBridge bridge = foliaScheduler;
        if (bridge == null) {
            synchronized (this) {
                if (foliaScheduler == null) {
                    foliaScheduler = new FoliaSchedulerBridge(plugin);
                }
                bridge = foliaScheduler;
            }
        }
        return bridge;
    }

    private Object scheduleBukkit(PlatformTaskRequest request) {
        BukkitScheduler scheduler = Bukkit.getScheduler();
        long delay = durationToTicks(request.getDelay());
        Duration interval = request.getInterval();
        Long period = interval == null ? null : durationToTicks(interval);
        if (request.getExecutionKind() == TaskExecution.Kind.ASYNC) {
            return scheduleBukkitAsync(scheduler, request.getCallback(), delay, period);
        }
        return scheduleBukkitGlobal(scheduler, request.getCallback(), delay, period);
    }

    private Object scheduleBukkitAsync(BukkitScheduler scheduler, Runnable callback, long delay, Long period) {
        if (period != null) {
            return scheduler.runTaskTimerAsynchronously(plugin, callback, delay, period);
        }
        if (delay > 0) {
            return scheduler.runTaskLaterAsynchronously(plugin, callback, delay);
        }
        return scheduler.runTaskAsynchronously(plugin, callback);
    }

    private Object scheduleBukkitGlobal(BukkitScheduler scheduler, Runnable callback, long delay, Long period) {
        if (period != null) {
            return scheduler.runTaskTimer(plugin, callback, delay, period);
        }
        if (delay > 0) {
            return scheduler.runTaskLater(plugin, callback, delay);
        }
        return scheduler.runTask(plugin, callback);
    }
}
