package org.pnfolder.pnlibrary.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class BukkitLifecycleListenerTest {
    @Test
    void callbacksQueuedBeforeReadinessRunOnceWhenServerBecomesReady() throws Exception {
        BukkitLifecycleListener listener = new BukkitLifecycleListener(mock(Plugin.class), null);
        AtomicInteger calls = new AtomicInteger();

        listener.whenServerReady(calls::incrementAndGet);
        assertEquals(0, calls.get());

        markServerReady(listener);
        assertEquals(1, calls.get());

        markServerReady(listener);
        assertEquals(1, calls.get());
    }

    @Test
    void callbacksRegisteredAfterReadinessRunImmediately() throws Exception {
        BukkitLifecycleListener listener = new BukkitLifecycleListener(mock(Plugin.class), null);
        markServerReady(listener);
        AtomicInteger calls = new AtomicInteger();

        listener.whenServerReady(calls::incrementAndGet);

        assertEquals(1, calls.get());
    }

    @Test
    void closeDiscardsCallbacksThatHaveNotRun() throws Exception {
        BukkitLifecycleListener listener = new BukkitLifecycleListener(mock(Plugin.class), null);
        AtomicInteger calls = new AtomicInteger();

        listener.whenServerReady(calls::incrementAndGet);
        listener.close();
        markServerReady(listener);

        assertEquals(0, calls.get());
    }

    @Test
    void callbacksRegisteredAfterCloseAreIgnored() {
        BukkitLifecycleListener listener = new BukkitLifecycleListener(mock(Plugin.class), null);
        AtomicInteger calls = new AtomicInteger();

        listener.close();
        listener.whenServerReady(calls::incrementAndGet);

        assertEquals(0, calls.get());
    }

    private static void markServerReady(BukkitLifecycleListener listener) throws Exception {
        Method method = BukkitLifecycleListener.class.getDeclaredMethod("markServerReady");
        method.setAccessible(true);
        method.invoke(listener);
    }
}
