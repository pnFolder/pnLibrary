package org.pnfolder.pnlibrary.core.menu.style

import org.pnfolder.pnlibrary.api.config.ConfigSetting
import java.util.concurrent.TimeUnit

/** How numbers of the settings menu are written, stepped and cut into the buttons of the number editor. */
internal object ConfigNumbers {
    private const val LADDER = 4
    private const val TEN = 10.0
    private val SECOND = TimeUnit.SECONDS.toMillis(1)
    private val MINUTE = TimeUnit.MINUTES.toMillis(1)
    private val HOUR = TimeUnit.HOURS.toMillis(1)
    private val DAY = TimeUnit.DAYS.toMillis(1)
    private val PARTS = listOf(DAY to "д", HOUR to "ч", MINUTE to "мин", SECOND to "с", 1L to "мс")

    /** Sizes of one step for a time kept in each unit, smallest first. */
    private val TIME_STEPS = mapOf(
        TimeUnit.MILLISECONDS to listOf(1L, 100L, SECOND, 10 * SECOND),
        TimeUnit.SECONDS to listOf(SECOND, MINUTE, 10 * MINUTE, HOUR),
        TimeUnit.MINUTES to listOf(MINUTE, 10 * MINUTE, HOUR, DAY),
        TimeUnit.HOURS to listOf(HOUR, 6 * HOUR, DAY, 7 * DAY),
        TimeUnit.DAYS to listOf(DAY, 7 * DAY, 30 * DAY, 365 * DAY),
    )

    /** The value with its unit, and for a time also written out: `5400 сек (1 ч 30 мин)`. */
    fun shown(setting: ConfigSetting): String {
        val value = setting.value ?: return "?"
        val unit = setting.duration ?: return if (setting.unit.isBlank()) value else "$value ${setting.unit}"
        val millis = value.toDoubleOrNull()?.let { (it * unit.toMillis(1)).toLong() } ?: return value
        return "$value ${unitWord(unit)} (${human(millis)})"
    }

    /** A length of time in words: `1 ч 30 мин`. */
    fun human(millis: Long): String {
        if (millis <= 0) return "0 с"
        var rest = millis
        val words = PARTS.mapNotNull { (size, word) ->
            val count = rest / size
            rest %= size
            if (count > 0) "$count $word" else null
        }
        return words.take(LADDER).joinToString(" ")
    }

    /** The four step sizes of a number, smallest first: the label and the amount in the unit the field keeps. */
    fun ladder(setting: ConfigSetting): List<Pair<String, Double>> {
        val unit = setting.duration
        if (unit != null) {
            val one = unit.toMillis(1).coerceAtLeast(1)
            val sizes = TIME_STEPS[unit] ?: listOf(one, one * 10, one * 100, one * 1000)
            val ladder = sizes.map { human(it) to it.toDouble() / one }.filter { fits(it.second, setting) }.take(LADDER)
            return if (ladder.size >= 2) ladder else listOf("1" to 1.0, "10" to TEN)
        }
        val label = setting.unit.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
        val amounts = generateSequence(setting.step) { it * TEN }.take(LADDER).filter { fits(it, setting) }.toList()
            .let { if (it.size >= 2) it else listOf(setting.step, setting.step * TEN) }
        return amounts.map { ConfigLook.number(it) + label to it }
    }

    private fun fits(amount: Double, setting: ConfigSetting): Boolean {
        val low = setting.min
        val high = setting.max
        return low == null || high == null || amount <= high - low || amount == setting.step
    }

    fun unitWord(unit: TimeUnit): String = when (unit) {
        TimeUnit.MILLISECONDS -> "мс"
        TimeUnit.SECONDS -> "сек"
        TimeUnit.MINUTES -> "мин"
        TimeUnit.HOURS -> "ч"
        TimeUnit.DAYS -> "дн"
        else -> unit.name.lowercase()
    }
}
