package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogRoutes
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val ROUTES = UpdateCatalogRoutes("/pn updates", update = { product -> "/pn update $product" }, check = "/pn check")
internal const val DOWNLOAD = "pnlibrary.updates.download"
internal const val SETTINGS = "pnlibrary.updates.settings"
internal const val ROLLBACK = "pnlibrary.updates.rollback"
internal const val HOME_ROWS = 5
internal const val NETWORK_SLOT = 34
internal const val NETWORK_CHECK = 49
internal const val PROXY_LABEL = "Прокси"
internal const val NETWORK_UPDATE = 51
internal const val GROUP_ADD = 46
internal const val GROUP_PLUGINS = 47
internal const val GROUP_DELETE = 52
internal const val GROUPS_PERMISSION = "pnlibrary.groups"
internal const val HOME_CLOSE = 40
internal const val SUPPORT_SLOT = 38
internal const val RESTART_SLOT = 41
internal const val ROLLBACK_SLOT = 42
internal const val CARD_ROWS = 4
internal const val CARD_BACK = 31
internal const val SMALL_ROWS = 3
internal const val SUPPORT_TOOL_SLOT = 16
internal const val SMALL_BACK = 22
internal const val LIST_ROWS = 6
internal const val PAGE_SIZE = 45
internal const val HISTORY_PAGE = 10
internal const val FOOTER_START = 45
internal const val FOOTER_END = 54
internal const val BACK = 45
internal const val PREVIOUS = 48
internal const val NEXT = 50
internal const val REFRESH = 49
internal const val FILTER = 47
internal const val DATE_FILTER = 46
internal const val JOURNAL_HOURS = 24
internal const val JOURNAL_LIMIT = 200
internal const val MESSAGE_PREVIEW = 70
internal val LEVEL_CYCLE = listOf<String?>(null, "error", "warning", "info")
internal val LEVELS = mapOf("error" to "ошибки", "warning" to "предупреждения", "info" to "сведения")
internal const val CLOSE = 53
internal const val BUS = "pnlibrary.events.bus"
internal const val CYCLE_HOLDS = 3
internal const val HOURS_PER_DAY = 24
internal const val MINUTES_PER_HOUR = 60
internal const val TASKS = "pnlibrary.tasks"
internal const val DETAIL_ROWS = 5
internal const val DETAIL_BACK = 40
internal const val CHANGE_FIRST = 19
internal const val CHANGE_BUTTONS = 7
internal const val WRAP_WIDTH = 44
internal const val WRAP_LINES = 8
internal const val TRACE_LINES = 12
internal const val NOTE_LINES = 8
internal const val TIMES_SHOWN = 10
internal const val DETAILS_SHOWN = 8
internal const val TRACE_STEPS = 6
internal const val BAR_WIDTH = 12
internal const val NANOS_PER_MICRO = 1_000L
internal const val NANOS_PER_MILLI = 1_000_000L
internal val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
internal val DAY_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
internal const val SECONDS_PER_MINUTE = 60L
internal const val TITLE_LIMIT = 30
internal const val TIMES_PREVIEW = 5
internal const val BLOCKER_LINES = 3
internal const val CHECK_REOPEN_MILLIS = 3_000L
internal const val LEGACY_BLACK: Short = 15
internal const val LEGACY_PURPLE: Short = 10

/** Whether the menu may change the update channel; only `dev` works for now, so the choice is switched off. */
internal const val CHANNEL_SWITCH = false

internal val CHANNEL_ITEMS = mapOf(
    UpdateChannel.STABLE to "EMERALD",
    UpdateChannel.RC to "GOLD_INGOT",
    UpdateChannel.BETA to "IRON_INGOT",
    UpdateChannel.ALPHA to "REDSTONE",
    UpdateChannel.DEV to "COAL",
)
internal val CHANNEL_HINTS = mapOf(
    UpdateChannel.STABLE to "Только проверенные релизы.",
    UpdateChannel.RC to "Кандидаты в релиз и стабильные.",
    UpdateChannel.BETA to "Бета-версии, кандидаты и стабильные.",
    UpdateChannel.ALPHA to "Ранние версии: возможны ошибки.",
    UpdateChannel.DEV to "Все версии, включая ежедневные сборки.",
)
internal val HOLDS = listOf(
    "1 час" to Duration.ofHours(1),
    "6 часов" to Duration.ofHours(6),
    "1 день" to Duration.ofDays(1),
    "3 дня" to Duration.ofDays(3),
    "7 дней" to Duration.ofDays(7),
)
