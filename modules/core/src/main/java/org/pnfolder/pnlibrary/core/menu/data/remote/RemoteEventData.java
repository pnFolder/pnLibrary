package org.pnfolder.pnlibrary.core.menu.data.remote;

import org.pnfolder.pnlibrary.core.menu.data.MenuCalls;
import org.pnfolder.pnlibrary.core.menu.data.question.EventAbout;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo;
import org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventDay;
import org.pnfolder.pnlibrary.api.events.inspect.trace.EventTrace;
import org.pnfolder.pnlibrary.core.link.remote.DataClient;
import org.pnfolder.pnlibrary.core.menu.owner.OwnerInfo;

/** The event-bus questions of {@link RemoteMenuData}. */
abstract class RemoteEventData extends RemoteCalls {
    RemoteEventData(String target, DataClient client) {
        super(target, client);
    }

    @Override
    public @NotNull List<EventInfo> events() {
        return ask(MenuCalls.EVENTS, MenuWire.listOf(EventInfo.class));
    }

    @Override
    public @NotNull List<ListenerInfo> listeners() {
        return ask(MenuCalls.LISTENERS, MenuWire.listOf(ListenerInfo.class));
    }

    @Override
    public @NotNull List<EventDay> eventHistory(@Nullable String eventType) {
        return ask(MenuCalls.EVENT_HISTORY, MenuWire.listOf(EventDay.class), args("type", eventType));
    }

    @Override
    public int eventHistoryDays() {
        Integer days = ask(MenuCalls.EVENT_HISTORY_DAYS, Integer.class);
        return days;
    }

    @Override
    public @NotNull List<String> tracedEvents() {
        return ask(MenuCalls.TRACED_EVENTS, MenuWire.listOf(String.class));
    }

    @Override
    public @NotNull List<EventTrace> traces() {
        return ask(MenuCalls.TRACES, MenuWire.listOf(EventTrace.class));
    }

    @Override
    public void clearTraces() {
        ask(MenuCalls.CLEAR_TRACES, Boolean.class);
    }

    @Override
    public void setTracing(@NotNull String eventType, boolean enabled) {
        ask(MenuCalls.SET_TRACING, Boolean.class, args("type", eventType, "enabled", enabled));
    }

    @Override
    public @Nullable String exportEventHistoryJson() {
        return ask(MenuCalls.EXPORT_EVENT_HISTORY, String.class, args("json", true));
    }

    @Override
    public @Nullable String exportEventHistoryCsv() {
        return ask(MenuCalls.EXPORT_EVENT_HISTORY, String.class, args("json", false));
    }

    @Override
    public @Nullable EventAbout eventAbout(@NotNull String eventType) {
        return ask(MenuCalls.EVENT_ABOUT, EventAbout.class, args("type", eventType));
    }

    @Override
    public @Nullable OwnerInfo ownerNamed(@NotNull String name) {
        return ask(MenuCalls.OWNER_NAMED, OwnerInfo.class, args("name", name));
    }
}
