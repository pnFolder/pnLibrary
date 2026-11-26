package org.pnfolder.pnlibrary.core.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pnfolder.pnlibrary.core.events.EventFixtures.ignoringErrors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.events.Event;
import org.pnfolder.pnlibrary.api.events.ResultedEvent;
import org.pnfolder.pnlibrary.api.events.bus.EventScope;
import org.pnfolder.pnlibrary.api.events.listener.EventListenerRegistration;
import org.pnfolder.pnlibrary.api.events.listener.EventPriority;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.core.events.EventFixtures.AnnotatedListener;
import org.pnfolder.pnlibrary.core.events.EventFixtures.InvalidListener;
import org.pnfolder.pnlibrary.core.events.EventFixtures.ResultEvent;
import org.pnfolder.pnlibrary.core.events.EventFixtures.TestEvent;

class EventDispatcherTest {
    @Test
    void aOneTimeListenerRunsOnceAndCancellingItFirstPreventsTheRun() {
        List<String> calls = new ArrayList<String>();
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            EventScope scope = events.scope(PluginId.of("test"));
            scope.on(TestEvent.class).listenOnce(event -> calls.add("once"));
            scope.on(TestEvent.class).listenOnce(event -> calls.add("never")).close();
            events.callEvent(new TestEvent());
            events.callEvent(new TestEvent());
            assertEquals(Arrays.asList("once"), calls);
            assertEquals(0, events.getInspector().listenerCount(TestEvent.class));
        } finally {
            events.close();
        }
    }

    @Test
    void callReturnsOnlyAfterListenersRunInPriorityOrder() {
        List<String> calls = new ArrayList<String>();
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            EventScope scope = events.scope(PluginId.of("test"));
            scope.subscribe(TestEvent.class, EventPriority.HIGH, event -> calls.add("high"));
            scope.subscribe(Event.class, EventPriority.LOW, event -> calls.add("base"));
            scope.subscribe(TestEvent.class, EventPriority.LOW, event -> calls.add("low"));
            events.callEvent(new TestEvent());
            assertEquals(Arrays.asList("base", "low", "high"), calls);
        } finally {
            events.close();
        }
    }

    @Test
    void listenerTimingsAreMeasuredWithoutTracing() {
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            events.scope(PluginId.of("test")).subscribe(TestEvent.class, EventPriority.NORMAL, event -> sleep(5));
            events.callEvent(new TestEvent());
            events.callEvent(new TestEvent());
            org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo info = events.getInspector().listeners().get(0);
            assertEquals(1, events.getInspector().listeners().size());
            assertEquals(2, info.getInvocations());
            assertTrue(info.getTotalNanos() >= 8_000_000, "total " + info.getTotalNanos());
            assertTrue(info.getMaxNanos() >= 4_000_000 && info.getMaxNanos() <= info.getTotalNanos());
            assertTrue(info.getAverageNanos() > 0);
        } finally {
            events.close();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void deniedResultIsVisibleImmediatelyAndSkipsIgnoreDeniedListeners() {
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            EventScope scope = events.scope(PluginId.of("test"));
            scope.subscribe(ResultEvent.class, EventPriority.LOW, event -> {
                event.value = "handled";
                event.setResult(ResultedEvent.ComponentResult.denied(Component.text("nope")));
            });
            scope.subscribe(ResultEvent.class, EventPriority.NORMAL, true, event -> {
                throw new IllegalStateException("denied event must be skipped");
            });
            ResultEvent event = new ResultEvent();
            ResultEvent returned = scope.callEvent(event);
            assertSame(event, returned);
            assertEquals("handled", event.value);
            assertFalse(event.isAllowed());
            assertEquals(Component.text("nope"), event.getDenyReason());
        } finally {
            events.close();
        }
    }

    @Test
    void allowedResultKeepsIgnoreDeniedListenersRunning() {
        AtomicBoolean ran = new AtomicBoolean();
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            events.scope(PluginId.of("test")).subscribe(ResultEvent.class, EventPriority.NORMAL, true, event -> ran.set(true));
            ResultEvent event = new ResultEvent();
            assertTrue(event.isAllowed());
            events.callEvent(event);
            assertTrue(ran.get());
            assertNull(event.getDenyReason());
        } finally {
            events.close();
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void typedSubscriptionReceivesEveryResultedEvent() {
        AtomicInteger seen = new AtomicInteger();
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            events.scope(PluginId.of("test")).subscribe((Class) ResultedEvent.class, event -> seen.incrementAndGet());
            events.callEvent(new ResultEvent());
            events.callEvent(new ResultEvent());
            assertEquals(2, seen.get());
        } finally {
            events.close();
        }
    }

    @Test
    void annotatedHandlersRegisterInOrderAndCloseTogether() {
        List<String> calls = new ArrayList<String>();
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            EventListenerRegistration registration = events.scope(PluginId.of("test")).register(new AnnotatedListener(calls));
            events.callEvent(new TestEvent());
            assertEquals(Arrays.asList("early", "normal"), calls);
            assertEquals(2, registration.getHandlerCount());
            registration.close();
            events.callEvent(new TestEvent());
            assertEquals(2, calls.size());
        } finally {
            events.close();
        }
    }

    @Test
    void invalidAnnotatedSignatureFailsDuringRegistration() {
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            assertThrows(IllegalArgumentException.class, () -> events.scope(PluginId.of("test")).register(new InvalidListener()));
        } finally {
            events.close();
        }
    }

    @Test
    void listenerFailureIsLoggedAndIsolated() {
        List<Throwable> errors = new ArrayList<Throwable>();
        AtomicBoolean completed = new AtomicBoolean();
        EventDispatcher events = new EventDispatcher((plugin, message, error) -> errors.add(error));
        try {
            EventScope scope = events.scope(PluginId.of("test"));
            scope.subscribe(TestEvent.class, event -> {
                throw new IllegalStateException("broken");
            });
            scope.subscribe(TestEvent.class, event -> completed.set(true));
            events.callEvent(new TestEvent());
            assertTrue(completed.get());
            assertEquals(1, errors.size());
            assertEquals("broken", errors.get(0).getMessage());
        } finally {
            events.close();
        }
    }

    @Test
    void aFailingListenerIsLoggedAndSkippedButADyingJvmIsNotHidden() {
        List<Throwable> logged = new ArrayList<Throwable>();
        AtomicInteger after = new AtomicInteger();
        EventDispatcher events = new EventDispatcher((plugin, message, error) -> logged.add(error));
        try {
            EventScope scope = events.scope(PluginId.of("test"));
            scope.subscribe(TestEvent.class, EventPriority.LOW, event -> {
                throw new IllegalStateException("plugin bug");
            });
            scope.subscribe(TestEvent.class, EventPriority.HIGH, event -> after.incrementAndGet());
            events.callEvent(new TestEvent());
            assertEquals(1, logged.size());
            assertEquals(1, after.get());

            scope.subscribe(TestEvent.class, EventPriority.LOWEST, event -> {
                throw new OutOfMemoryError("simulated");
            });
            assertThrows(OutOfMemoryError.class, () -> events.callEvent(new TestEvent()));
        } finally {
            events.close();
        }
    }

    @Test
    void scopeAndServiceLifecycleIsEnforced() {
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        PluginId id = PluginId.of("test");
        EventScope scope = events.scope(id);
        assertSame(scope, events.scope(id));
        events.unregisterAll(id);
        assertTrue(scope.isClosed());
        assertThrows(IllegalStateException.class, () -> scope.subscribe(TestEvent.class, event -> { }));
        events.close();
        assertThrows(IllegalStateException.class, () -> events.callEvent(new TestEvent()));
    }

    @Test
    void dispatchUsesCallingThread() {
        Thread callingThread = Thread.currentThread();
        AtomicReference<Thread> listenerThread = new AtomicReference<Thread>();
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            events.scope(PluginId.of("test")).subscribe(TestEvent.class, event -> listenerThread.set(Thread.currentThread()));
            events.callEvent(new TestEvent());
            assertSame(callingThread, listenerThread.get());
        } finally {
            events.close();
        }
    }

    @Test
    void builderAppliesPriorityAndIgnoreDeniedThenRegistersOnListen() {
        List<String> calls = new ArrayList<String>();
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            EventScope scope = events.scope(PluginId.of("test"));
            scope.on(ResultEvent.class).priority(EventPriority.LOWEST).listen(event ->
                    event.setResult(ResultedEvent.ComponentResult.denied(Component.text("no"))));
            scope.on(ResultEvent.class).priority(EventPriority.LOW).listen(event -> calls.add("low"));
            scope.on(ResultEvent.class).ignoreDenied().listen(event -> calls.add("skipped-once-denied"));
            scope.on(ResultEvent.class).priority(EventPriority.HIGH).listen(event -> calls.add("high"));

            events.callEvent(new ResultEvent());
            assertEquals(Arrays.asList("low", "high"), calls);
        } finally {
            events.close();
        }
    }

    @Test
    void builderRegistersNothingUntilListenIsCalled() {
        AtomicBoolean ran = new AtomicBoolean();
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            EventScope scope = events.scope(PluginId.of("test"));
            scope.on(ResultEvent.class).priority(EventPriority.HIGH).ignoreDenied();
            events.callEvent(new ResultEvent());
            assertFalse(ran.get());
            scope.on(ResultEvent.class).listen(event -> ran.set(true));
            events.callEvent(new ResultEvent());
            assertTrue(ran.get());
        } finally {
            events.close();
        }
    }
}
