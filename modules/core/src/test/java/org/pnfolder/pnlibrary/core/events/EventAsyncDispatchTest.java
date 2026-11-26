package org.pnfolder.pnlibrary.core.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.pnfolder.pnlibrary.core.events.EventFixtures.ignoringErrors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.events.ResultedEvent;
import org.pnfolder.pnlibrary.api.events.bus.DispatchMode;
import org.pnfolder.pnlibrary.api.events.listener.EventPriority;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.core.events.EventFixtures.ResultEvent;
import org.pnfolder.pnlibrary.core.events.EventFixtures.TestEvent;

class EventAsyncDispatchTest {
    @Test
    void callResultReturnsTheTypedResultTheListenersLeft() {
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        org.pnfolder.pnlibrary.api.runtime.PnLibrary library = org.mockito.Mockito.mock(org.pnfolder.pnlibrary.api.runtime.PnLibrary.class);
        org.mockito.Mockito.when(library.getEvents()).thenReturn(events);
        org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider.install(library);
        try {
            events.scope(PluginId.of("test")).subscribe(ResultEvent.class, EventPriority.NORMAL, event ->
                    event.setResult(ResultedEvent.ComponentResult.denied(Component.text("closed"))));

            ResultedEvent.ComponentResult result = new ResultEvent().callResult();

            assertFalse(result.isAllowed());
            assertEquals(Component.text("closed"), result.getReason());
        } finally {
            org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider.clear(library);
            events.close();
        }
    }

    @Test
    void asyncCallRunsListenersOnTheGivenExecutorAndCompletesWithTheSameEvent() throws Exception {
        AtomicReference<Thread> listenerThread = new AtomicReference<Thread>();
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> new Thread(task, "event-test-worker"));
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            events.scope(PluginId.of("test")).subscribe(TestEvent.class, event -> listenerThread.set(Thread.currentThread()));
            TestEvent event = new TestEvent();
            TestEvent done = events.callEventAsync(event, executor).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertSame(event, done);
            assertEquals("event-test-worker", listenerThread.get().getName());
        } finally {
            events.close();
            executor.shutdownNow();
        }
    }

    @Test
    void eventKnowsItsDispatchModeAndThread() throws Exception {
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            List<Boolean> seen = new CopyOnWriteArrayList<Boolean>();
            events.scope(PluginId.of("test")).subscribe(TestEvent.class, event -> seen.add(event.isAsync()));
            TestEvent event = new TestEvent();
            assertNull(event.getDispatchMode());
            events.callEvent(event);
            assertEquals(DispatchMode.SYNC, event.getDispatchMode());
            events.callEventAsync(event).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals("pnLibrary-events", event.getDispatchThread().getName());
            assertEquals(Arrays.asList(false, true), new ArrayList<Boolean>(seen));
        } finally {
            events.close();
        }
    }
}
