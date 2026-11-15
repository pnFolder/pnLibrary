package org.pnfolder.pnlibrary.core.menu.style

import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogRoutes
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val ROUTES = UpdateCatalogRoutes("/pn updates", update = { product -> "/pn update $product" }, check = "/pn check")
internal const val SETTINGS = "pnlibrary.updates.settings"
internal const val ROLLBACK = "pnlibrary.updates.rollback"
internal const val HOME_ROWS = 5
internal const val NETWORK_CHECK = 49
internal const val GROUPS_PERMISSION = "pnlibrary.groups"
internal const val HOME_CLOSE = 40
internal const val SMALL_ROWS = 3
internal const val SMALL_BACK = 22
internal const val PAGE_SIZE = 45
internal const val HISTORY_PAGE = 10
internal const val FOOTER_END = 54
internal const val BACK = 45
internal const val PREVIOUS = 48
internal const val NEXT = 50
internal const val FILTER = 47
internal const val DATE_FILTER = 46
internal const val JOURNAL_HOURS = 24
internal const val JOURNAL_LIMIT = 200
internal const val MESSAGE_PREVIEW = 70
internal const val BUS = "pnlibrary.events.bus"
internal const val TASKS = "pnlibrary.tasks"
internal const val STATS = "pnlibrary.stats"
internal const val PLACEHOLDERS = "pnlibrary.placeholders"
internal const val CONFIGS = "pnlibrary.configs"
internal const val DETAIL_ROWS = 5
internal const val DETAIL_BACK = 40
internal const val WRAP_WIDTH = 44
internal const val BAR_WIDTH = 12
internal val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
internal const val TITLE_LIMIT = 30
/** Whether the menu may change the update channel; only `dev` works for now, so the choice is switched off. */
internal val HOLDS = listOf(
    "1 час" to Duration.ofHours(1),
    "6 часов" to Duration.ofHours(6),
    "1 день" to Duration.ofDays(1),
    "3 дня" to Duration.ofDays(3),
    "7 дней" to Duration.ofDays(7),
)
