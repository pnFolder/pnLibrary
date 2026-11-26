package org.pnfolder.pnlibrary.core.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pnfolder.pnlibrary.core.events.EventFixtures.ignoringErrors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.events.bus.EventScope;
import org.pnfolder.pnlibrary.api.events.listener.EventPriority;
import org.pnfolder.pnlibrary.api.events.listener.EventSubscription;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.core.events.EventFixtures.ResultEvent;
import org.pnfolder.pnlibrary.core.events.EventFixtures.TestEvent;

class EventDispatchCacheTest {
    @Test
    void listenersAddedOrRemovedAfterAWarmCallTakeEffectOnTheNextCall() {
        List<String> calls = new ArrayList<String>();
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            EventScope scope = events.scope(PluginId.of("test"));
            EventSubscription first = scope.subscribe(TestEvent.class, EventPriority.NORMAL, event -> calls.add("first"));
            events.callEvent(new TestEvent());
            assertEquals(Arrays.asList("first"), calls);

            scope.subscribe(TestEvent.class, EventPriority.LOW, event -> calls.add("earlier"));
            events.callEvent(new TestEvent());
            assertEquals(Arrays.asList("first", "earlier", "first"), calls);

            first.close();
            events.callEvent(new TestEvent());
            assertEquals(Arrays.asList("first", "earlier", "first", "earlier"), calls);
        } finally {
            events.close();
        }
    }

    @Test
    void closingAPluginScopeRemovesItsListenersFromAnAlreadyCachedDispatch() {
        AtomicInteger ran = new AtomicInteger();
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            PluginId id = PluginId.of("test");
            events.scope(id).subscribe(TestEvent.class, event -> ran.incrementAndGet());
            events.callEvent(new TestEvent());
            events.unregisterAll(id);
            events.callEvent(new TestEvent());
            assertEquals(1, ran.get());
        } finally {
            events.close();
        }
    }

    @Test
    void anEventNobodyListensToDispatchesWithoutErrorAndIsNotRetained() {
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            for (int index = 0; index < 1_000; index++) {
                events.callEvent(new TestEvent());
            }
        } finally {
            events.close();
        }
    }

    @Test
    void changingListenersOfOneEventLeavesAnotherEventsDispatchCorrect() {
        List<String> calls = new ArrayList<String>();
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            EventScope scope = events.scope(PluginId.of("test"));
            scope.subscribe(ResultEvent.class, event -> calls.add("result"));
            scope.subscribe(TestEvent.class, event -> calls.add("test"));
            events.callEvent(new ResultEvent());
            events.callEvent(new TestEvent());

            for (int index = 0; index < 50; index++) {
                scope.subscribe(TestEvent.class, event -> calls.add("churn")).close();
            }
            calls.clear();
            events.callEvent(new ResultEvent());
            events.callEvent(new TestEvent());
            assertEquals(Arrays.asList("result", "test"), calls);
        } finally {
            events.close();
        }
    }

    @Test
    void aListenerAddedWhileOtherThreadsDispatchIsSeenByTheNextCall() throws Exception {
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        AtomicBoolean running = new AtomicBoolean(true);
        List<Thread> readers = new ArrayList<Thread>();
        try {
            EventScope scope = events.scope(PluginId.of("test"));
            for (int index = 0; index < 4; index++) {
                Thread reader = new Thread(() -> {
                    while (running.get()) {
                        events.callEvent(new TestEvent());
                    }
                });
                reader.start();
                readers.add(reader);
            }
            for (int round = 0; round < 300; round++) {
                AtomicInteger seen = new AtomicInteger();
                EventSubscription subscription = scope.subscribe(TestEvent.class, event -> seen.incrementAndGet());
                int before = seen.get();
                events.callEvent(new TestEvent());
                assertTrue(seen.get() > before, "round " + round + ": listener subscribed before the call was not invoked");
                subscription.close();
            }
        } finally {
            running.set(false);
            for (Thread reader : readers) {
                reader.join(5_000);
            }
            events.close();
        }
    }
}
