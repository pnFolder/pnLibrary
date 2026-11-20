package org.pnfolder.pnlibrary.core.commands.events

internal const val ROOT = "/pn bus"
internal const val PAGE_SIZE = 8
internal const val TOP_SIZE = 10
internal val RANGES = linkedMapOf("1h" to 1, "24h" to 24, "7d" to 168, "30d" to 720)
internal const val DAYS_PER_PAGE = 7
internal const val EVENTS_PER_PAGE = 6
internal const val BAR_WIDTH = 12
internal val DAY = java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale("ru"))
internal const val NANOS_PER_MICRO = 1_000L
internal const val NANOS_PER_MILLI = 1_000_000L
