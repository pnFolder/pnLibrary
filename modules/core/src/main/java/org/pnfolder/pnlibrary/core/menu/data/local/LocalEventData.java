package org.pnfolder.pnlibrary.core.menu.data.local;

import org.pnfolder.pnlibrary.core.menu.data.question.EventAbout;
import java.nio.file.Path;
import java.util.List;
import org.pnfolder.pnlibrary.api.events.EventDescription;
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo;
import org.pnfolder.pnlibrary.api.events.inspect.EventInspector;
import org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventDay;
import org.pnfolder.pnlibrary.api.events.inspect.trace.EventTrace;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.menu.owner.PluginDirectory;

/** The event-bus facts of {@link LocalMenuData}. */
final class LocalEventData {
    private final PnLibrary library;
    private final PluginDirectory directory;

    LocalEventData(PnLibrary library, PluginDirectory directory) {
        this.library = library;
        this.directory = directory;
    }

    private EventInspector bus() {
        return library.getEvents().getInspector();
    }

    List<EventInfo> events() {
        return bus().events();
    }

    List<ListenerInfo> listeners() {
        return bus().listeners();
    }

    List<EventDay> history(String eventType) {
        return bus().history(eventType);
    }

    int historyDays() {
        return bus().getHistoryDays();
    }

    List<String> tracedEvents() {
        return bus().tracedEvents();
    }

    List<EventTrace> traces() {
        return bus().traces();
    }

    void clearTraces() {
        bus().clearTraces();
    }

    void setTracing(String eventType, boolean enabled) {
        Class<? extends org.pnfolder.pnlibrary.api.events.Event> type = bus().eventType(eventType);
        if (type == null) {
            return;
        }
        if (enabled) {
            bus().enableTracing(type);
        } else {
            bus().disableTracing(type);
        }
    }

    String exportJson() {
        Path file = bus().exportHistoryJson();
        return file == null ? null : file.toString();
    }

    String exportCsv() {
        Path file = bus().exportHistoryCsv();
        return file == null ? null : file.toString();
    }

    EventAbout about(String eventType) {
        Class<? extends org.pnfolder.pnlibrary.api.events.Event> type = bus().eventType(eventType);
        if (type == null) {
            return null;
        }
        EventDescription about = type.getAnnotation(EventDescription.class);
        return new EventAbout(about == null ? "" : about.purpose(), about == null ? "" : about.usage(), directory.ownerOf(type));
    }
}
