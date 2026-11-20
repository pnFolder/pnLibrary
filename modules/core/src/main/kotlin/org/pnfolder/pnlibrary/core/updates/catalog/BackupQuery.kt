package org.pnfolder.pnlibrary.core.updates.catalog

import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * How the list of rollback sets is ordered.
 *
 * @property word the word written in the `sort` command
 * @property title the name shown on the page
 */
enum class BackupOrder(val word: String, val title: String) {
    /** Newest first. */
    NEWEST("new", "сначала новые"),

    /** Oldest first; sets without a recorded date come last. */
    OLDEST("old", "сначала старые"),

    /** By plugin name, newest first inside one plugin. */
    PLUGIN("plugin", "по плагину"),
}

/**
 * What the administrator asked of the list of rollback sets: only sets that touch one plugin, only recent ones, and in
 * which order. The same value builds the command that shows the list again, so page and sort buttons keep the filters.
 *
 * @property plugin product the sets must contain, or null for all
 * @property since oldest creation time that still counts, or null for any; a set without a recorded date never matches
 * @property sinceText what was typed for [since], kept to rebuild the command
 * @property order how the matching sets are ordered
 */
data class BackupQuery(
    val plugin: String? = null,
    val since: Instant? = null,
    val sinceText: String? = null,
    val order: BackupOrder = BackupOrder.NEWEST,
) {
    /** Whether no filter and the usual order are set. */
    val isDefault: Boolean get() = plugin == null && since == null && order == BackupOrder.NEWEST

    /** The sets that match, in the requested order. */
    fun apply(backups: List<UpdateBackup>): List<UpdateBackup> {
        val matching = backups.filter { backup ->
            (plugin == null || backup.components.any { it.product.value.equals(plugin, ignoreCase = true) }) &&
                (since == null || backup.createdAt?.let { !it.isBefore(since) } == true)
        }
        return when (order) {
            BackupOrder.NEWEST -> matching.sortedByDescending { it.createdAt ?: Instant.MIN }
            BackupOrder.OLDEST -> matching.sortedBy { it.createdAt ?: Instant.MAX }
            BackupOrder.PLUGIN -> matching.sortedWith(
                compareBy<UpdateBackup> { backup -> backup.components.minOfOrNull { it.product.value.lowercase() } ?: "" }
                    .thenByDescending { it.createdAt ?: Instant.MIN },
            )
        }
    }

    /** The command that shows this list, built on [base] such as `/pn updates backups`; a page number can be appended. */
    fun command(base: String): String = buildString {
        append(base)
        plugin?.let { append(" plugin ").append(it) }
        sinceText?.let { append(" since ").append(it) }
        if (order != BackupOrder.NEWEST) append(" sort ").append(order.word)
    }

    /** Parses and builds queries. */
    companion object {
        /** What `since` accepts, for the error message. */
        const val SINCE_HELP = "7d (за 7 дней) или дата 2026-10-31"

        /** The moment [text] means: `7d` is that many days before [now], `2026-10-31` is the start of that day in [zone]; null otherwise. */
        fun parseSince(text: String, now: Instant, zone: ZoneId): Instant? {
            val days = Regex("(\\d{1,4})d", RegexOption.IGNORE_CASE).matchEntire(text)?.groupValues?.get(1)?.toLongOrNull()
            if (days != null) return now.minus(days, ChronoUnit.DAYS)
            return runCatching { LocalDate.parse(text).atStartOfDay(zone).toInstant() }.getOrNull()
        }

        /** The order that [word] names, or null. */
        fun orderOf(word: String): BackupOrder? = BackupOrder.values().firstOrNull { it.word.equals(word, ignoreCase = true) }
    }
}
