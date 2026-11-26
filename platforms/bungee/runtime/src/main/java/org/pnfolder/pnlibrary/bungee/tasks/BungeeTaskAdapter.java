package org.pnfolder.pnlibrary.bungee.tasks;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.api.scheduler.TaskScheduler;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest;
import org.pnfolder.pnlibrary.spi.tasks.TrackingPlatformTaskAdapter;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

// BungeeCord runs every execution kind on the same asynchronous scheduler.
/** Schedules pnLibrary tasks on the BungeeCord scheduler. */
public final class BungeeTaskAdapter extends TrackingPlatformTaskAdapter {
    private final Function<PlatformTaskRequest, PlatformTaskHandle> nativeSchedule;

    /** Creates an adapter that schedules the tasks of {@code plugin}. */
    public BungeeTaskAdapter(@NotNull Plugin plugin) {
        this(request -> schedule(plugin, request));
    }

    BungeeTaskAdapter(@NotNull Function<PlatformTaskRequest, PlatformTaskHandle> nativeSchedule) {
        this.nativeSchedule = nativeSchedule;
    }

    private static PlatformTaskHandle schedule(Plugin plugin, PlatformTaskRequest request) {
        TaskScheduler scheduler = plugin.getProxy().getScheduler();
        Duration interval = request.getInterval();
        long delay = request.getDelay().toMillis();
        ScheduledTask task = interval == null
                ? scheduler.schedule(plugin, request.getCallback(), delay, TimeUnit.MILLISECONDS)
                : scheduler.schedule(plugin, request.getCallback(), delay, interval.toMillis(), TimeUnit.MILLISECONDS);
        return () -> {
            task.cancel();
            return true;
        };
    }

    @NotNull
    @Override
    protected PlatformTaskHandle scheduleNative(@NotNull PlatformTaskRequest request) {
        return nativeSchedule.apply(request);
    }
}
