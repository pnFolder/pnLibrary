package org.pnfolder.pnlibrary.core.menu.data

/** Names of the calls the menu sends to another server over the link. */
object MenuCalls {
    const val VERSION = "version"
    const val SNAPSHOTS = "snapshots"
    const val SNAPSHOT = "snapshot"
    const val PLUGINS = "plugins"
    const val PLAN = "plan"
    const val HELD_UNTIL = "heldUntil"
    const val HISTORY_COUNT = "historyCount"
    const val HISTORY_PAGE = "historyPage"
    const val BACKUPS = "backups"
    const val CHECK_ALL = "checkAll"
    const val CHECK_ONE = "checkOne"
    const val SET_AUTOMATIC = "setAutomatic"
    const val SET_CHANNEL = "setChannel"
    const val SET_JAVA_BUILD = "setJavaBuild"
    const val SET_HELD = "setHeld"
    const val ROLLBACK = "rollback"
    const val RESTORE_DATABASE = "restore-database"
    const val EXTRACT_BACKUP = "extract-backup"
    const val JOURNAL_GROUPS = "journalGroups"
    const val JOURNAL_DETAIL = "journalDetail"
    const val TASKS = "tasks"
    const val STATS = "stats"
    const val PLACEHOLDERS = "placeholders"
    const val SCENARIO_DEFINITIONS = "scenarioDefinitions"
    const val RUN_TASK = "runTask"
    const val CANCEL_TASK = "cancelTask"
    const val MODULES = "modules"
    const val LIBRARIES = "libraries"
    const val DISK = "disk"
    const val MODULE_ACTION = "moduleAction"
    const val EVENTS = "events"
    const val LISTENERS = "listeners"
    const val EVENT_HISTORY = "eventHistory"
    const val EVENT_HISTORY_DAYS = "eventHistoryDays"
    const val TRACED_EVENTS = "tracedEvents"
    const val TRACES = "traces"
    const val CLEAR_TRACES = "clearTraces"
    const val SET_TRACING = "setTracing"
    const val EXPORT_EVENT_HISTORY = "exportEventHistory"
    const val EVENT_ABOUT = "eventAbout"
    const val OWNER_NAMED = "ownerNamed"
    const val COMMAND = "command"
    const val GROUPS = "groups"
    const val EDIT_GROUP = "editGroup"
    const val SETTINGS = "settings"
    const val CHANGE_SETTING = "changeSetting"

    /** The calls that change nothing, whose answers may be remembered for a moment. */
    val READS = setOf(
        GROUPS,
        VERSION, SNAPSHOTS, SNAPSHOT, PLUGINS, PLAN, HELD_UNTIL, HISTORY_COUNT, HISTORY_PAGE, BACKUPS, JOURNAL_GROUPS, JOURNAL_DETAIL,
        TASKS, MODULES, LIBRARIES, DISK, STATS,
        PLACEHOLDERS, SCENARIO_DEFINITIONS, SETTINGS,
        EVENTS, LISTENERS, EVENT_HISTORY, EVENT_HISTORY_DAYS, TRACED_EVENTS, TRACES, EVENT_ABOUT, OWNER_NAMED,
    )
}
