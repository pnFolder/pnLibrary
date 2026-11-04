package org.pnfolder.pnlibrary.core.commands.events

import org.pnfolder.pnlibrary.api.events.EventInfo

internal const val ROOT = "/pn bus"
internal const val PAGE_SIZE = 8
internal const val TOP_SIZE = 10
internal val RANGES = linkedMapOf("1h" to 1, "24h" to 24, "7d" to 168, "30d" to 720)
internal const val MAX_TRACES = 6
internal const val DAYS_PER_PAGE = 7
internal const val EVENTS_PER_PAGE = 6
internal const val HOVER_EVENTS = 5
internal const val MAX_SUGGESTED_DAYS = 31
internal const val BAR_WIDTH = 12
internal val DAY = java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale("ru"))
internal const val NANOS_PER_MICRO = 1_000L
internal const val NANOS_PER_MILLI = 1_000_000L

/** Tab words in order, with what each one keeps. */
internal val FILTERS: List<Pair<String, (EventInfo) -> Boolean>> = listOf(
    "all" to { _: EventInfo -> true },
    "listened" to { e: EventInfo -> e.listeners > 0 },
    "called" to { e: EventInfo -> e.calls > 0 },
    "denied" to { e: EventInfo -> e.denied > 0 },
)
internal val LABELS = mapOf("all" to "все", "listened" to "со слушателями", "called" to "вызывались", "denied" to "с отказами")
