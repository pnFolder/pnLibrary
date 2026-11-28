package org.pnfolder.pnlibrary.core.menu.style;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Every permission that opens a page of the menu, each named once. */
public final class MenuPermissions {
    public static final String ADMIN = "pnlibrary.admin";
    public static final String SERVERS = "pnlibrary.servers";
    public static final String UPDATES_VIEW = "pnlibrary.updates.view";
    public static final String UPDATES_DOWNLOAD = "pnlibrary.updates.download";
    public static final String UPDATES_ROLLBACK = "pnlibrary.updates.rollback";
    public static final String UPDATES_SETTINGS = "pnlibrary.updates.settings";
    public static final String UPDATES_RESTART = "pnlibrary.updates.restart";
    public static final String EVENTS_VIEW = "pnlibrary.events.view";
    public static final String EVENTS_BUS = "pnlibrary.events.bus";
    public static final String TASKS = "pnlibrary.tasks";
    public static final String MODULES = "pnlibrary.modules";
    public static final String STATS = "pnlibrary.stats";
    public static final String PLACEHOLDERS = "pnlibrary.placeholders";
    public static final String CHANNELS = "pnlibrary.channels";
    public static final String ACTIONS = "pnlibrary.actions";
    public static final String DEBUG = "pnlibrary.debug";
    public static final String CURRENCY_LIST = "pnlibrary.currency.list";
    public static final String LIBRARY_SETTINGS = "pnlibrary.settings";

    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
        ADMIN, SERVERS, UPDATES_VIEW, UPDATES_DOWNLOAD, UPDATES_ROLLBACK, UPDATES_SETTINGS, UPDATES_RESTART,
        EVENTS_VIEW, EVENTS_BUS, TASKS, MODULES, STATS, PLACEHOLDERS, CHANNELS, ACTIONS, DEBUG, CURRENCY_LIST, LIBRARY_SETTINGS));

    private MenuPermissions() {
    }
}
