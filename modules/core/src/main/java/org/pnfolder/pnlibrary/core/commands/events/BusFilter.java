package org.pnfolder.pnlibrary.core.commands.events;

import java.util.NoSuchElementException;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo;

/** A way to narrow the event list: the word typed after {@code /pn bus}, its label on the tab, and which events it keeps. */
public enum BusFilter {
    ALL("all", "все") {
        @Override
        public boolean keeps(@NotNull EventInfo event) {
            return true;
        }
    },
    LISTENED("listened", "со слушателями") {
        @Override
        public boolean keeps(@NotNull EventInfo event) {
            return event.getListeners() > 0;
        }
    },
    CALLED("called", "вызывались") {
        @Override
        public boolean keeps(@NotNull EventInfo event) {
            return event.getCalls() > 0;
        }
    },
    DENIED("denied", "с отказами") {
        @Override
        public boolean keeps(@NotNull EventInfo event) {
            return event.getDenied() > 0;
        }
    };

    private final String word;
    private final String label;

    BusFilter(String word, String label) {
        this.word = word;
        this.label = label;
    }

    @NotNull
    public String getWord() {
        return word;
    }

    @NotNull
    public String getLabel() {
        return label;
    }

    public abstract boolean keeps(@NotNull EventInfo event);

    /** The filter typed as {@code word}. */
    @NotNull
    public static BusFilter named(@NotNull String word) {
        for (BusFilter filter : values()) {
            if (filter.word.equals(word)) {
                return filter;
            }
        }
        throw new NoSuchElementException("No bus filter named " + word);
    }
}
