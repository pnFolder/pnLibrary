package org.pnfolder.pnlibrary.core.journal

/**
 * One journal event with everything the journal knows about it, for a menu.
 *
 * @property id the id that `/pn events view <id>` opens
 * @property level `info`, `warning` or `error`
 * @property source plugin or component that wrote the event
 * @property message the whole text of the newest repeat
 * @property stackTrace the stack trace with its causes, or `null` when the event has none
 * @property details other facts the event carries, such as a player or a version
 * @property times when it happened, newest first, in epoch milliseconds
 */
data class JournalDetail(
    val id: String,
    val level: String,
    val source: String,
    val message: String,
    val stackTrace: String?,
    val details: Map<String, String>,
    val times: List<Long>,
)

/**
 * Equal journal events merged into one line, for menus.
 *
 * @property id the id that `/pn events view <id>` opens
 * @property level `info`, `warning` or `error`
 * @property source plugin or component that wrote the event
 * @property message the first line of the event text
 * @property count how many times it happened in the window
 * @property lastMillis when it happened last, in epoch milliseconds
 */
data class JournalGroup(
    val id: String,
    val level: String,
    val source: String,
    val message: String,
    val count: Int,
    val lastMillis: Long,
)
