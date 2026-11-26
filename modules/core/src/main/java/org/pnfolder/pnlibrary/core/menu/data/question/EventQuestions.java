package org.pnfolder.pnlibrary.core.menu.data.question;

import org.pnfolder.pnlibrary.core.menu.data.MenuData;
import org.pnfolder.pnlibrary.core.menu.data.MenuCalls;

/** Events, listeners, traces and their history that another server asks for. */
public final class EventQuestions {
    private EventQuestions() {
    }

    /** Answers {@code ask} when it is one of these questions; false when it is not. */
    public static boolean answer(MenuData data, MenuQuestion ask) {
        switch (ask.method()) {
            case MenuCalls.EVENTS:
                ask.give(data.events());
                return true;
            case MenuCalls.LISTENERS:
                ask.give(data.listeners());
                return true;
            case MenuCalls.EVENT_HISTORY:
                ask.give(data.eventHistory(ask.text("type")));
                return true;
            case MenuCalls.EVENT_HISTORY_DAYS:
                ask.give(data.eventHistoryDays());
                return true;
            case MenuCalls.TRACED_EVENTS:
                ask.give(data.tracedEvents());
                return true;
            case MenuCalls.TRACES:
                ask.give(data.traces());
                return true;
            case MenuCalls.CLEAR_TRACES:
                data.clearTraces();
                ask.give(true);
                return true;
            case MenuCalls.SET_TRACING:
                data.setTracing(ask.textOrEmpty("type"), ask.flag("enabled"));
                ask.give(true);
                return true;
            case MenuCalls.EXPORT_EVENT_HISTORY:
                ask.give(ask.flag("json") ? data.exportEventHistoryJson() : data.exportEventHistoryCsv());
                return true;
            case MenuCalls.EVENT_ABOUT:
                ask.give(data.eventAbout(ask.textOrEmpty("type")));
                return true;
            default:
                return false;
        }
    }
}
