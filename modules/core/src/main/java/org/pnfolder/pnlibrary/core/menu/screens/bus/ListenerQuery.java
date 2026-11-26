package org.pnfolder.pnlibrary.core.menu.screens.bus;

import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo;
import org.pnfolder.pnlibrary.core.menu.MenuViewer;

/** Which listeners a list shows and in what order. */
final class ListenerQuery {
    final String title;
    final Consumer<MenuViewer> back;
    final Predicate<ListenerInfo> keep;
    final Comparator<ListenerInfo> order;

    ListenerQuery(String title, Consumer<MenuViewer> back, Predicate<ListenerInfo> keep, Comparator<ListenerInfo> order) {
        this.title = title;
        this.back = back;
        this.keep = keep;
        this.order = order;
    }

    ListenerQuery(String title, Consumer<MenuViewer> back, Predicate<ListenerInfo> keep) {
        this(title, back, keep, null);
    }

    ListenerQuery(String title, Consumer<MenuViewer> back) {
        this(title, back, info -> true, null);
    }
}
