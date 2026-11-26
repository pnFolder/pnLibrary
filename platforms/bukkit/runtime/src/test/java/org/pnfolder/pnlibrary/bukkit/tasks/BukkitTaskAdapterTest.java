package org.pnfolder.pnlibrary.bukkit.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest;
import org.pnfolder.pnlibrary.spi.tasks.TrackingPlatformTaskAdapter;

class BukkitTaskAdapterTest {
    @Test
    void durationConversionRoundsPartialTicksUp() {
        assertEquals(0, BukkitTaskAdapter.durationToTicks(Duration.ZERO));
        assertEquals(1, BukkitTaskAdapter.durationToTicks(Duration.ofMillis(1)));
        assertEquals(20, BukkitTaskAdapter.durationToTicks(Duration.ofSeconds(1)));
    }

    @Test
    void oneShotTaskIsReleasedAfterCallback() {
        RecordingTaskAdapter adapter = new RecordingTaskAdapter();
        PlatformTaskRequest request = new PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, () -> { });
        PlatformTaskHandle handle = adapter.buildTask().from(request).schedule();
        assertEquals(1, adapter.active);
        adapter.callback.run();
        assertEquals(true, handle.cancel());
        assertEquals(0, adapter.active);
    }

    private static final class RecordingTaskAdapter extends TrackingPlatformTaskAdapter {
        private Runnable callback;
        private int active;

        @Override
        protected PlatformTaskHandle scheduleNative(@NotNull PlatformTaskRequest request) {
            callback = request.getCallback();
            active++;
            return () -> {
                active--;
                return true;
            };
        }
    }
}
