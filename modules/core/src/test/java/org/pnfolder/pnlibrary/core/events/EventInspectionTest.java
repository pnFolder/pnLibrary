package org.pnfolder.pnlibrary.core.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pnfolder.pnlibrary.core.events.EventFixtures.ignoringErrors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.events.ResultedEvent;
import org.pnfolder.pnlibrary.api.events.bus.DispatchMode;
import org.pnfolder.pnlibrary.api.events.bus.EventScope;
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo;
import org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo;
import org.pnfolder.pnlibrary.api.events.inspect.trace.EventTrace;
import org.pnfolder.pnlibrary.api.events.listener.EventPriority;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.core.events.EventFixtures.ResultEvent;
import org.pnfolder.pnlibrary.core.events.EventFixtures.TestEvent;

class EventInspectionTest {
    @Test
    void tracingRecordsEachListenerWithItsEffectOnTheResult() {
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            EventScope scope = events.scope(PluginId.of("test"));
            scope.subscribe(ResultEvent.class, EventPriority.LOW, event ->
                    event.setResult(ResultedEvent.ComponentResult.denied(Component.text("nope"))));
            scope.subscribe(ResultEvent.class, EventPriority.NORMAL, true, event -> {
                throw new IllegalStateException("skipped");
            });
            scope.subscribe(ResultEvent.class, EventPriority.HIGH, event -> {
                throw new IllegalStateException("boom");
            });
            events.callEvent(new ResultEvent());
            assertTrue(events.getInspector().traces().isEmpty());

            events.getInspector().enableTracing(ResultEvent.class, 2);
            for (int index = 0; index < 3; index++) {
                events.callEvent(new ResultEvent());
            }
            List<EventTrace> traces = events.getInspector().traces();
            assertEquals(2, traces.size());
            EventTrace trace = traces.get(traces.size() - 1);
            assertFalse(trace.getAllowed());
            assertEquals("denied", trace.getResult());
            assertEquals(3, trace.getSteps().size());
            assertTrue(trace.getSteps().get(0).getChangedResult());
            assertEquals("allowed", trace.getSteps().get(0).getResultBefore());
            assertTrue(trace.getSteps().get(1).getSkipped());
            assertTrue(trace.getSteps().get(2).getFailed());

            events.getInspector().disableTracing(ResultEvent.class);
            events.callEvent(new ResultEvent());
            assertEquals(2, events.getInspector().traces().size());
            events.getInspector().clearTraces();
            assertTrue(events.getInspector().traces().isEmpty());
        } finally {
            events.close();
        }
    }

    @Test
    void aTracedTypeWithASmallCapacityKeepsTheCallsOfAnotherTracedType() {
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            events.getInspector().enableTracing(ResultEvent.class, 3);
            events.getInspector().enableTracing(TestEvent.class, 1);
            for (int index = 0; index < 3; index++) {
                events.callEvent(new ResultEvent());
            }
            events.callEvent(new TestEvent());
            events.callEvent(new TestEvent());

            int results = 0;
            int tests = 0;
            for (EventTrace trace : events.getInspector().traces()) {
                results += trace.getEventName().equals(new ResultEvent().getEventName()) ? 1 : 0;
                tests += trace.getEventName().equals(new TestEvent().getEventName()) ? 1 : 0;
            }
            assertEquals(3, results);
            assertEquals(1, tests);
        } finally {
            events.close();
        }
    }

    @Test
    void asyncCallWithoutExecutorUsesTheBusThreadAndTracesTheMode() throws Exception {
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            events.scope(PluginId.of("test")).subscribe(TestEvent.class, event -> { });
            events.getInspector().enableTracing(TestEvent.class);
            String caller = Thread.currentThread().getName();
            events.callEventAsync(new TestEvent()).toCompletableFuture().get(5, TimeUnit.SECONDS);
            events.callEvent(new TestEvent());
            List<EventTrace> traces = events.getInspector().traces();
            assertEquals(DispatchMode.ASYNC, traces.get(0).getMode());
            assertEquals("pnLibrary-events", traces.get(0).getThread());
            assertEquals(DispatchMode.SYNC, traces.get(1).getMode());
            assertEquals(caller, traces.get(1).getThread());
        } finally {
            events.close();
        }
    }

    @Test
    void listenersListsSubscribersBeforeAnyEventIsCalled() {
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            EventScope a = events.scope(PluginId.of("m-a"), PluginId.of("shop"), ModuleId.of("cart"));
            EventScope b = events.scope(PluginId.of("b"));
            a.subscribe(ResultEvent.class, EventPriority.HIGH, event -> { });
            b.subscribe(ResultEvent.class, EventPriority.LOW, true, event -> {
                throw new IllegalStateException("boom");
            });
            a.subscribe(TestEvent.class, EventPriority.NORMAL, event -> { });

            assertEquals(2, events.getInspector().listenerCount(ResultEvent.class));
            List<ListenerInfo> result = events.getInspector().listeners(ResultEvent.class);
            assertEquals(Arrays.asList(EventPriority.LOW, EventPriority.HIGH),
                    Arrays.asList(result.get(0).getPriority(), result.get(1).getPriority()));
            assertTrue(result.get(0).getIgnoreDenied());
            assertEquals("shop", result.get(1).getPlugin().getValue());
            assertEquals("cart", result.get(1).getModule().getValue());
            assertNull(result.get(0).getModule());
            assertEquals(0, result.get(0).getInvocations());
            assertEquals(3, events.getInspector().listeners().size());

            events.callEvent(new ResultEvent());
            List<ListenerInfo> after = events.getInspector().listeners(ResultEvent.class);
            assertEquals(1, after.get(0).getInvocations());
            assertEquals(1, after.get(0).getFailures());
            assertEquals(0, after.get(1).getFailures());
        } finally {
            events.close();
        }
    }

    @Test
    void inspectorCountsCallsAndResolvesEventTypesByName() {
        EventDispatcher events = new EventDispatcher(ignoringErrors());
        try {
            events.scope(PluginId.of("a")).subscribe(ResultEvent.class, event -> { });
            assertEquals(ResultEvent.class, events.getInspector().eventType(ResultEvent.class.getName()));
            events.callEvent(new ResultEvent());
            events.callEvent(new ResultEvent());
            EventInfo info = events.getInspector().event(ResultEvent.class);
            assertEquals(1, info.getListeners());
            assertEquals(2, info.getCalls());
            List<PluginId> plugins = new ArrayList<PluginId>(events.getInspector().plugins(ResultEvent.class));
            assertEquals(Arrays.asList(PluginId.of("a")), plugins);
        } finally {
            events.close();
        }
    }
}
