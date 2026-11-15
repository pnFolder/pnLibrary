package org.pnfolder.pnlibrary.core.menu.style

/** Every permission that opens a page of the menu, each named once. */
internal object MenuPermissions {
    const val ADMIN = "pnlibrary.admin"
    const val SERVERS = "pnlibrary.servers"
    const val UPDATES_VIEW = "pnlibrary.updates.view"
    const val UPDATES_DOWNLOAD = "pnlibrary.updates.download"
    const val UPDATES_ROLLBACK = "pnlibrary.updates.rollback"
    const val UPDATES_SETTINGS = "pnlibrary.updates.settings"
    const val UPDATES_RESTART = "pnlibrary.updates.restart"
    const val EVENTS_VIEW = "pnlibrary.events.view"
    const val EVENTS_BUS = "pnlibrary.events.bus"
    const val TASKS = "pnlibrary.tasks"
    const val STATS = "pnlibrary.stats"
    const val PLACEHOLDERS = "pnlibrary.placeholders"
    const val DEBUG = "pnlibrary.debug"
    const val CURRENCY_LIST = "pnlibrary.currency.list"
    const val LIBRARY_SETTINGS = "pnlibrary.settings"

    val ALL = listOf(
        ADMIN, SERVERS, UPDATES_VIEW, UPDATES_DOWNLOAD, UPDATES_ROLLBACK, UPDATES_SETTINGS, UPDATES_RESTART,
        EVENTS_VIEW, EVENTS_BUS, TASKS, STATS, PLACEHOLDERS, DEBUG, CURRENCY_LIST, LIBRARY_SETTINGS,
    )
}
