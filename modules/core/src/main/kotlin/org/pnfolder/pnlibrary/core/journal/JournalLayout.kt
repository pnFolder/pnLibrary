package org.pnfolder.pnlibrary.core.journal

/** rows shown on one page of `/pn events`. */
internal const val PAGE_SIZE = 5

/** Longest message shown in a player's chat so lines do not flood it; the console and the card show all. */
internal const val CHAT_CHARS = 46

/** Records read to fold repeats and to count how often an event happened. */
internal const val SCAN_LIMIT = 5_000

/** Width in characters at which hover text is wrapped, and how many message lines a hover shows. */
internal const val HOVER_WIDTH = 44

internal const val HOVER_MESSAGE_LINES = 4

/** Width at which the message in the card is wrapped. */
internal const val CARD_WIDTH = 56

/** Occurrence times listed on the repeats page. */
internal const val REPEAT_ROWS = 15
