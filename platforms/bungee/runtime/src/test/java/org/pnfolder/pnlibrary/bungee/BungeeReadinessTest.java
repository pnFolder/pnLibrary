package org.pnfolder.pnlibrary.bungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.TaskScheduler;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.logging.PnLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Checks that startup-dependent work waits until the Bungee adapter is marked ready. */
class BungeeReadinessTest {

    @Test
    void startupCallbacksWaitForRuntimePublicationAndAreReleasedOnce() {
        Plugin plugin = mock(Plugin.class);
        ProxyServer proxy = mock(ProxyServer.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getScheduler()).thenReturn(scheduler);
        when(plugin.getDataFolder()).thenReturn(new File("build/readiness-test"));
        List<Runnable> scheduled = new ArrayList<>();
        doAnswer(invocation -> {
            scheduled.add(invocation.getArgument(1));
            return null;
        }).when(scheduler).runAsync(same(plugin), any(Runnable.class));
        BungeePlatformAdapter adapter = new BungeePlatformAdapter(plugin, () -> mock(PnLogger.class));
        AtomicInteger calls = new AtomicInteger();
        try {
            adapter.whenServerReady(calls::incrementAndGet);
            assertTrue(scheduled.isEmpty());
            adapter.markReady();
            adapter.markReady();
            assertEquals(1, scheduled.size());
            scheduled.remove(0).run();
            assertEquals(1, calls.get());
            adapter.whenServerReady(calls::incrementAndGet);
            assertEquals(1, scheduled.size());
            scheduled.remove(0).run();
            assertEquals(2, calls.get());
        } finally {
            adapter.close();
        }
        adapter.whenServerReady(calls::incrementAndGet);
        assertTrue(scheduled.isEmpty());
    }

    @Test
    void closingBeforeReadinessDiscardsPendingStartupCallbacks() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(new File("build/readiness-test"));
        BungeePlatformAdapter adapter = new BungeePlatformAdapter(plugin, () -> mock(PnLogger.class));
        adapter.whenServerReady(() -> fail("Closed adapter must not release startup work"));
        adapter.close();
        adapter.markReady();
        verify(plugin, never()).getProxy();
    }
}
