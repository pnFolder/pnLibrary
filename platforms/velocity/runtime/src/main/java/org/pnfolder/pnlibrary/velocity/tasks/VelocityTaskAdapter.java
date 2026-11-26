package org.pnfolder.pnlibrary.velocity.tasks;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest;
import org.pnfolder.pnlibrary.spi.tasks.TrackingPlatformTaskAdapter;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

/** Schedules pnLibrary tasks on the Velocity scheduler on behalf of one registered plugin. */
public final class VelocityTaskAdapter extends TrackingPlatformTaskAdapter {
    private final Consumer<String> warning;
    private final Function<PlatformTaskRequest, PlatformTaskHandle> nativeSchedule;

    /** Creates an adapter that schedules under {@code plugin}; {@code warning} receives entity-task notices. */
    public VelocityTaskAdapter(
            @NotNull ProxyServer server, @NotNull Object plugin, @NotNull Consumer<String> warning) {
        this(warning, request -> schedule(server, plugin, request));
    }

    VelocityTaskAdapter(
            @NotNull Consumer<String> warning,
            @NotNull Function<PlatformTaskRequest, PlatformTaskHandle> nativeSchedule) {
        this.warning = warning;
        this.nativeSchedule = nativeSchedule;
    }

    private static PlatformTaskHandle schedule(ProxyServer server, Object plugin, PlatformTaskRequest request) {
        if (!server.getPluginManager().fromInstance(plugin).isPresent()) {
            throw new IllegalArgumentException("Task owner must be a registered Velocity plugin");
        }
        Scheduler.TaskBuilder builder = server.getScheduler().buildTask(plugin, request.getCallback());
        if (!request.getDelay().isZero()) {
            builder.delay(request.getDelay());
        }
        Duration interval = request.getInterval();
        if (interval != null) {
            builder.repeat(interval);
        }
        ScheduledTask task = builder.schedule();
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

    // Velocity has no entity scheduler; entity tasks fall back to the proxy scheduler.
    @Override
    protected void beforeSchedule(@NotNull PlatformTaskRequest request) {
        if (request.getExecutionKind() == TaskExecution.Kind.ENTITY) {
            warning.accept("Entity-bound task requested on Velocity, which has no entity scheduler; "
                    + "running on the proxy scheduler instead.");
        }
    }
}
