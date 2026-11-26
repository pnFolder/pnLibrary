package org.pnfolder.pnlibrary.core.menu.screens;

import java.time.LocalDate;
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo;
import org.pnfolder.pnlibrary.common.time.DisplayZone;

/** Filter applied to the event list. */
enum BusFilter {
    ALL("все"),
    LISTENED("со слушателями"),
    CALLED("вызывались"),
    DENIED("с отказами"),
    TODAY("сегодня");

    private final String label;

    BusFilter(String label) {
        this.label = label;
    }

    String getLabel() {
        return label;
    }

    boolean keep(EventInfo info) {
        switch (this) {
            case LISTENED:
                return info.getListeners() > 0;
            case CALLED:
                return info.getCalls() > 0;
            case DENIED:
                return info.getDenied() > 0;
            case TODAY:
                return info.getLastCalledAt() != null
                    && info.getLastCalledAt().atZone(DisplayZone.getZone()).toLocalDate().equals(LocalDate.now(DisplayZone.getZone()));
            default:
                return true;
        }
    }
}
