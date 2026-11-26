package org.pnfolder.pnlibrary.velocity;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.spi.metrics.NoopMetricsFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Checks that startup-dependent work waits until the Velocity adapter is marked ready. */
class VelocityReadinessTest {
    @TempDir
    Path directory;

    @Test
    void startupWorkWaitsForReadyAndIsNotScheduledTwice() {
        List<Runnable> callbacks = new ArrayList<>();
        VelocityPlatformAdapter adapter = adapter(callbacks);
        AtomicInteger calls = new AtomicInteger();

        adapter.whenServerReady(calls::incrementAndGet);
        assertEquals(0, callbacks.size());
        adapter.markReady();
        adapter.markReady();
        assertEquals(1, callbacks.size());
        callbacks.get(0).run();
        assertEquals(1, calls.get());
        adapter.whenServerReady(calls::incrementAndGet);
        assertEquals(2, callbacks.size());
        adapter.close();
    }

    @Test
    void closedAdapterDiscardsPendingReadinessWork() {
        List<Runnable> callbacks = new ArrayList<>();
        VelocityPlatformAdapter adapter = adapter(callbacks);

        adapter.whenServerReady(() -> { });
        adapter.close();
        adapter.markReady();
        adapter.whenServerReady(() -> { });

        assertEquals(0, callbacks.size());
    }

    private VelocityPlatformAdapter adapter(List<Runnable> callbacks) {
        ProxyServer proxy = mock(ProxyServer.class, RETURNS_DEEP_STUBS);
        Object owner = new Object();
        when(proxy.getPluginManager().fromInstance(owner)).thenReturn(Optional.of(mock(PluginContainer.class)));
        Scheduler.TaskBuilder builder = proxy.getScheduler().buildTask(owner, (Runnable) () -> { });
        when(proxy.getScheduler().buildTask(eq(owner), any(Runnable.class))).thenAnswer(invocation -> {
            callbacks.add(invocation.getArgument(1));
            return builder;
        });
        return new VelocityPlatformAdapter(owner, proxy, NoopMetricsFactory.INSTANCE, directory);
    }
}
