package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

/** The formats every placeholder can use. Numbers and dates follow the language of the viewer. */
internal object BuiltInFormats {
    private const val NOBREAK = " "

    fun register(registry: FormatRegistry) {
        fun add(name: String, input: Class<*>, apply: (Any, List<String>, ViewerInfo) -> Any?) =
            registry.add(FormatDef("", name, input, apply = apply))

        // numbers
        add("group", Number::class.java) { n, _, v -> group(n as Number, v) }
        add("short", Number::class.java) { n, _, v -> short(n as Number, v) }
        add("round", Number::class.java) { n, a, v -> round(n as Number, a.firstOrNull()?.toIntOrNull() ?: 0, v) }
        add("percent", Number::class.java) { n, _, v -> round((n as Number).toDouble() * 100, 1, v) + "%" }
        add("pad", Number::class.java) { n, a, _ -> (n as Number).toLong().toString().padStart(a.firstOrNull()?.toIntOrNull() ?: 2, '0') }
        add("sign", Number::class.java) { n, _, _ -> (n as Number).toDouble().let { if (it > 0) "+${n.toLong()}" else n.toLong().toString() } }
        add("roman", Number::class.java) { n, _, _ -> roman((n as Number).toInt()) }
        add("ifpos", Number::class.java) { n, a, _ -> if ((n as Number).toDouble() > 0) a.getOrElse(0) { "" } else a.getOrElse(1) { "" } }

        // text
        add("upper", Any::class.java) { t, _, _ -> t.toString().uppercase() }
        add("lower", Any::class.java) { t, _, _ -> t.toString().lowercase() }
        add("title", Any::class.java) { t, _, _ -> t.toString().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } }
        add("cut", Any::class.java) { t, a, _ ->
            val limit = a.firstOrNull()?.toIntOrNull() ?: 16
            val text = t.toString()
            if (text.length <= limit) text else text.take(limit) + "…"
        }
        add("wrap", Any::class.java) { t, a, _ -> a.getOrElse(0) { "[" } + t + a.getOrElse(1) { "]" } }
        add("default", Any::class.java) { t, _, _ -> t }

        // yes or no
        add("yesno", Boolean::class.javaObjectType) { b, _, v -> yesNo(b as Boolean, v) }
        add("icon", Boolean::class.javaObjectType) { b, _, _ -> if (b as Boolean) "✔" else "✘" }

        // a table of words for any value
        add("map", Any::class.java) { value, a, _ ->
            a.map { it.split('=', limit = 2) }.firstOrNull { it.size == 2 && it[0] == value.toString() }?.get(1) ?: value
        }

        // time
        add("left", Duration::class.java) { d, _, v -> left(d as Duration, v) }
        add("left", Number::class.java) { n, _, v -> left(Duration.ofSeconds((n as Number).toLong()), v) }
        add("hms", Duration::class.java) { d, _, _ -> hms(d as Duration) }
        add("hms", Number::class.java) { n, _, _ -> hms(Duration.ofSeconds((n as Number).toLong())) }
    }

    private fun russian(@Viewer viewer: ViewerInfo) = viewer.language.startsWith("ru", ignoreCase = true)

    private fun group(number: Number, @Viewer viewer: ViewerInfo): String {
        val text = BigDecimal(number.toString()).toPlainString()
        val integer = text.substringBefore('.')
        val fraction = if ('.' in text) text.substringAfter('.') else ""
        val sign = if (integer.startsWith("-")) "-" else ""
        val digits = integer.removePrefix("-").reversed().chunked(3).joinToString(if (russian(viewer)) NOBREAK else ",").reversed()
        return sign + digits + if (fraction.isEmpty()) "" else (if (russian(viewer)) "," else ".") + fraction
    }

    private fun round(number: Number, places: Int, @Viewer viewer: ViewerInfo): String {
        val text = BigDecimal(number.toDouble()).setScale(places.coerceIn(0, 8), RoundingMode.HALF_UP).toPlainString()
        return if (russian(viewer)) text.replace('.', ',') else text
    }

    private fun short(number: Number, @Viewer viewer: ViewerInfo): String {
        val value = number.toDouble()
        val (divider, suffix) = when {
            kotlin.math.abs(value) >= 1e9 -> 1e9 to "B"
            kotlin.math.abs(value) >= 1e6 -> 1e6 to "M"
            kotlin.math.abs(value) >= 1e3 -> 1e3 to "K"
            else -> return number.toLong().toString()
        }
        return round(value / divider, 1, viewer) + suffix
    }

    private fun roman(number: Int): String {
        if (number !in 1..3999) return number.toString()
        val symbols = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
            50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
        )
        var rest = number
        return buildString { for ((size, symbol) in symbols) while (rest >= size) { append(symbol); rest -= size } }
    }

    private fun yesNo(value: Boolean, @Viewer viewer: ViewerInfo): String =
        if (russian(viewer)) (if (value) "да" else "нет") else (if (value) "yes" else "no")

    private fun left(duration: Duration, @Viewer viewer: ViewerInfo): String {
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60
        val units = if (russian(viewer)) listOf("д", "ч", "м", "с") else listOf("d", "h", "m", "s")
        val parts = listOf(days, hours, minutes, seconds).zip(units).filter { it.first > 0 }.take(2)
        return if (parts.isEmpty()) "0" + units.last() else parts.joinToString(" ") { "${it.first}${it.second}" }
    }

    private fun hms(duration: Duration): String =
        "%02d:%02d:%02d".format(duration.toHours(), duration.toMinutes() % 60, duration.seconds % 60)
}
