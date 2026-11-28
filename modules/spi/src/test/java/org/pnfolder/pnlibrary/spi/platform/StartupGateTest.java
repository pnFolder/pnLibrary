package org.pnfolder.pnlibrary.spi.platform;

import org.pnfolder.pnlibrary.spi.platform.lifecycle.PlatformLifecycle;
import org.pnfolder.pnlibrary.spi.platform.lifecycle.StartupGate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StartupGateTest {
    private final PlatformLifecycle lifecycle = new PlatformLifecycle();
    private final List<String> dispatched = new ArrayList<>();
    private final StartupGate gate = new StartupGate(lifecycle, Runnable::run);

    private Runnable task(String name) {
        return () -> dispatched.add(name);
    }

    @Test
    void queuedWorkIsReleasedOnceInSubmissionOrder() {
        gate.whenReady(task("a"));
        gate.whenReady(task("b"));
        assertEquals(Collections.emptyList(), dispatched);

        gate.markReady();
        gate.markReady();
        assertEquals(Arrays.asList("a", "b"), dispatched);
    }

    @Test
    void workSubmittedAfterReadinessIsDispatchedImmediately() {
        gate.markReady();
        gate.whenReady(task("late"));
        assertEquals(Collections.singletonList("late"), dispatched);
    }

    @Test
    void closedLifecycleDropsQueuedAndNewWork() {
        gate.whenReady(task("queued"));
        lifecycle.close();
        gate.markReady();
        gate.whenReady(task("after"));
        assertEquals(Collections.emptyList(), dispatched);
    }

    @Test
    void discardedWorkIsNeverReleased() {
        gate.whenReady(task("discarded"));
        gate.discardPending();
        gate.markReady();
        assertEquals(Collections.emptyList(), dispatched);
    }
}
