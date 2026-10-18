package ru.privatenull.pnlibrary.common.text

import java.time.Duration
import kotlin.math.abs

/**
 * Russian wording shared by every message: number agreement and short durations with one set of abbreviations
 * (`мин`, `ч`, `д`), so the catalog, the console and placeholders never disagree.
 */
object RussianText {
    /** The form of a noun that agrees with [count]: `1 плагин`, `2 плагина`, `5 плагинов`. */
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

    /** [count] followed by the agreeing form: `3 набора`. */
    @JvmStatic
    fun counted(count: Long, one: String, few: String, many: String): String = "$count ${plural(count, one, few, many)}"

    /** The largest whole unit of [duration]: `3 д`, `5 ч`, `20 мин`; at least one minute. */
    @JvmStatic
    fun period(duration: Duration): String = when {
        duration.toDays() >= 1 -> "${duration.toDays()} д"
        duration.toHours() >= 1 -> "${duration.toHours()} ч"
        else -> "${maxOf(1, duration.toMinutes())} мин"
    }

    /** Time left until something ends: `ещё 3 д`; hours are kept up to two days so `ещё 30 ч` stays precise. */
    @JvmStatic
    fun remaining(left: Duration): String = when {
        left.toHours() in 1..47 -> "ещё ${left.toHours()} ч"
        else -> "ещё ${period(left)}"
    }

    /** How long ago something happened: `только что`, `5 мин назад`, `3 ч назад`, `12 д назад`. */
    @JvmStatic
    fun ago(elapsed: Duration): String = when {
        elapsed.isNegative || elapsed.toMinutes() < 1 -> "только что"
        else -> "${period(elapsed)} назад"
    }
}
