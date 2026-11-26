package org.pnfolder.pnlibrary.core.menu.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Names of the calls the menu sends to another server over the link. */
public final class MenuCalls {
    public static final String VERSION = "version";
    public static final String SNAPSHOTS = "snapshots";
    public static final String SNAPSHOT = "snapshot";
    public static final String PLUGINS = "plugins";
    public static final String PLAN = "plan";
    public static final String HELD_UNTIL = "heldUntil";
    public static final String HISTORY_COUNT = "historyCount";
    public static final String HISTORY_PAGE = "historyPage";
    public static final String BACKUPS = "backups";
    public static final String CHECK_ALL = "checkAll";
    public static final String CHECK_ONE = "checkOne";
    public static final String SET_CHANNEL = "setChannel";
    public static final String SET_JAVA_BUILD = "setJavaBuild";
    public static final String SET_HELD = "setHeld";
    public static final String ROLLBACK = "rollback";
    public static final String RESTORE_DATABASE = "restore-database";
    public static final String EXTRACT_BACKUP = "extract-backup";
    public static final String JOURNAL_GROUPS = "journalGroups";
    public static final String JOURNAL_DETAIL = "journalDetail";
    public static final String TASKS = "tasks";
    public static final String STATS = "stats";
    public static final String PLACEHOLDERS = "placeholders";
    public static final String SCENARIO_DEFINITIONS = "scenarioDefinitions";
    public static final String RUN_TASK = "runTask";
    public static final String CANCEL_TASK = "cancelTask";
    public static final String MODULES = "modules";
    public static final String LIBRARIES = "libraries";
    public static final String DISK = "disk";
    public static final String MODULE_ACTION = "moduleAction";
    public static final String EVENTS = "events";
    public static final String LISTENERS = "listeners";
    public static final String EVENT_HISTORY = "eventHistory";
    public static final String EVENT_HISTORY_DAYS = "eventHistoryDays";
    public static final String TRACED_EVENTS = "tracedEvents";
    public static final String TRACES = "traces";
    public static final String CLEAR_TRACES = "clearTraces";
    public static final String SET_TRACING = "setTracing";
    public static final String EXPORT_EVENT_HISTORY = "exportEventHistory";
    public static final String EVENT_ABOUT = "eventAbout";
    public static final String OWNER_NAMED = "ownerNamed";
    public static final String COMMAND = "command";
    public static final String GROUPS = "groups";
    public static final String EDIT_GROUP = "editGroup";
    public static final String SETTINGS = "settings";
    public static final String CHANGE_SETTING = "changeSetting";

    /** The calls that change nothing, whose answers may be remembered for a moment. */
    public static final Set<String> READS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        GROUPS, VERSION, SNAPSHOTS, SNAPSHOT, PLUGINS, PLAN, HELD_UNTIL, HISTORY_COUNT, HISTORY_PAGE, BACKUPS,
        JOURNAL_GROUPS, JOURNAL_DETAIL, TASKS, MODULES, LIBRARIES, DISK, STATS, PLACEHOLDERS,
        SCENARIO_DEFINITIONS, SETTINGS, EVENTS, LISTENERS, EVENT_HISTORY, EVENT_HISTORY_DAYS, TRACED_EVENTS,
        TRACES, EVENT_ABOUT, OWNER_NAMED)));

    private MenuCalls() {
    }
}
