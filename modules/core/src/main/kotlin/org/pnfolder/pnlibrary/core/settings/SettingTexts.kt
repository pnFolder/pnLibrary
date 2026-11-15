package org.pnfolder.pnlibrary.core.settings

import java.time.Duration

/** Reading and writing the values of settings as text. */
internal object SettingTexts {
    private val PART = Regex("""(\d+(?:[.,]\d+)?)\s*(ms|мс|сек|с|s|мин|м|m|ч|h|д|d)""")
    private val ON = setOf("true", "on", "yes", "да", "вкл", "1")
    private val OFF = setOf("false", "off", "no", "нет", "выкл", "0")
    private const val DAY = 86_400_000L
    private const val HOUR = 3_600_000L
    private const val MINUTE = 60_000L
    private const val SECOND = 1_000L

    /** `true` or `false` from the usual words for them. */
    fun switch(text: String): Boolean = when (text.trim().lowercase()) {
        in ON -> true
        in OFF -> false
        else -> throw SettingException("Нужно значение true или false (можно on, off, да, нет).")
    }

    /** A whole number inside [min]..[max]. */
    fun number(text: String, min: Long, max: Long): Int {
        val value = text.trim().toLongOrNull() ?: throw SettingException("Нужно целое число от $min до $max.")
        if (value !in min..max) throw SettingException("Число $value вне допустимых границ: от $min до $max.")
        return value.toInt()
    }

    /** A length of time such as `6h`, `1h30m`, `90 мин` or `45s`, inside [min]..[max] milliseconds. */
    fun duration(text: String, min: Long, max: Long): Duration {
        val cleaned = text.trim().lowercase()
        val parts = PART.findAll(cleaned).toList()
        if (parts.isEmpty() || parts.joinToString("") { it.value }.replace(" ", "") != cleaned.replace(" ", "")) {
            throw SettingException("Нужно время с единицей, например 30s, 15m, 6h, 1h30m (с, м, ч, д тоже годятся).")
        }
        val millis = parts.sumOf { part -> (part.groupValues[1].replace(',', '.').toDouble() * millisOf(part.groupValues[2])).toLong() }
        if (millis !in min..max) {
            throw SettingException("Время ${duration(millis)} вне допустимых границ: от ${duration(min)} до ${duration(max)}.")
        }
        return Duration.ofMillis(millis)
    }

    /** A length of time as `1д2ч30м15с`, the shortest form that reads back to the same time. */
    fun duration(value: Duration): String = duration(value.toMillis())

    fun duration(millis: Long): String {
        if (millis <= 0) return "0с"
        var rest = millis
        return buildString {
            for ((size, name) in listOf(DAY to "д", HOUR to "ч", MINUTE to "м", SECOND to "с", 1L to "мс")) {
                val count = rest / size
                rest %= size
                if (count > 0) append(count).append(name)
            }
        }
    }

    private fun millisOf(letters: String): Long = when (letters) {
        "ms", "мс" -> 1L
        "с", "s", "сек" -> SECOND
        "м", "m", "мин" -> MINUTE
        "ч", "h" -> HOUR
        else -> DAY
    }
}
