package org.pnfolder.pnlibrary.common.text

import java.time.Duration
import kotlin.math.abs

/**
 * Russian number agreement and short durations. One set of abbreviations keeps the catalog, console and
 * placeholders consistent.
 */
object RussianText {
    /** Picks the noun form that agrees with [count], e.g. `1 плагин`, `2 плагина`, `5 плагинов`. */
    @JvmStatic
    fun plural(count: Long, one: String, few: String, many: String): String {
        val absolute = abs(count)
        return when {
            absolute % 100 in 11..14 -> many
            absolute % 10 == 1L -> one
            absolute % 10 in 2..4 -> few
            else -> many
        }
    }

    @JvmStatic
    fun counted(count: Long, one: String, few: String, many: String): String = "$count ${plural(count, one, few, many)}"

    /** Formats [duration] in one unit, rounded down, never below one minute: `3 д`, `5 ч`, `20 мин`. */
    @JvmStatic
    fun period(duration: Duration): String = when {
        duration.toDays() >= 1 -> "${duration.toDays()} д"
        duration.toHours() >= 1 -> "${duration.toHours()} ч"
        else -> "${maxOf(1, duration.toMinutes())} мин"
    }

    /** Time left until something ends. Hours are kept up to two days so the value stays precise. */
    @JvmStatic
    fun remaining(left: Duration): String = when {
        left.toHours() in 1..47 -> "ещё ${left.toHours()} ч"
        else -> "ещё ${period(left)}"
    }

    /** How long ago something happened, or "just now" for under a minute. */
    @JvmStatic
    fun ago(elapsed: Duration): String = when {
        elapsed.isNegative || elapsed.toMinutes() < 1 -> "только что"
        else -> "${period(elapsed)} назад"
    }
}
