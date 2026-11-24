package org.pnfolder.pnlibrary.core.settings

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * One way to put a list of choices in order, for example by alphabet or by UTC offset.
 *
 * @property label the name shown on the sorting button
 * @property sort puts the values in order as they are at the moment [Instant] (an offset changes with summer time)
 */
internal class ChoiceOrder(val label: String, val sort: (List<String>, Instant) -> List<String>)

/**
 * A list of values a setting is chosen from, for settings that have too many values for a switch.
 *
 * The list is computed where it is needed (java.time is on every server, proxy and client), so the link between a proxy
 * and a game server sends only the [id], never the values.
 *
 * @property id the name sent over the link and used by the menu to find the list again
 * @property all every value, alphabetically and without repeats
 * @property orders the ways to sort the list; the first one is shown at first
 * @param curated the values worth showing first to somebody who does not know the name, in the order they are shown;
 * values that are not in [all] are dropped
 * @param cycled the values a click on the menu button rolls through, in their order; empty means every value of [all]
 * @property details lines `label to value` that tell about a value, so it can be picked without knowing its name
 */
internal class ChoiceSource(
    val id: String,
    val all: List<String>,
    val orders: List<ChoiceOrder> = listOf(ALPHABET),
    curated: List<String> = emptyList(),
    cycled: List<String> = emptyList(),
    private val details: (String, Instant) -> List<Pair<String, String>> = { _, _ -> emptyList() },
) {
    /** The curated values, in their own order. */
    val popular: List<String> = curated.filter { it in all }

    /** The values a click rolls through: the [cycled] ones that exist, or all values. */
    val cycle: List<String> = cycled.filter { it in all }.ifEmpty { all }

    /**
     * The values the rolling switcher shows for the [current] one: the [cycle], with [current] put first when it is not
     * in it (a zone nobody listed as popular is still shown, as the value in use).
     */
    fun rolling(current: String): List<String> = if (current in cycle) cycle else listOf(current) + cycle

    /** The value after [current] in the [cycle], wrapping around; a [current] outside the cycle moves to the first value. */
    fun next(current: String): String {
        val at = cycle.indexOf(current)
        return if (at >= 0) cycle[Math.floorMod(at + 1, cycle.size)] else cycle.first()
    }

    /** The value before [current] in the [cycle], wrapping around; a [current] outside the cycle moves to the last value. */
    fun previous(current: String): String {
        val at = cycle.indexOf(current)
        return if (at >= 0) cycle[Math.floorMod(at - 1, cycle.size)] else cycle.last()
    }

    /** The lore lines of [value] as they are at the moment [at]. */
    fun lore(value: String, at: Instant = Instant.now()): List<Pair<String, String>> = details(value, at)

    /**
     * What the picker shows: the values that contain [filter] (see [matches]) sorted by the order number [order], or, with
     * no filter, either the [popular] ones or everything.
     */
    fun view(order: Int, onlyPopular: Boolean, filter: String, at: Instant = Instant.now()): List<String> {
        val sorted = orders[order.coerceIn(orders.indices)].sort(all, at)
        return when {
            filter.isNotBlank() -> sorted.filter { matches(it, filter) }
            onlyPopular -> popular
            else -> sorted
        }
    }

    companion object {
        /** The id of [ZONES]. */
        const val ZONE_ID = "zones"

        private val ALPHABET = ChoiceOrder("по алфавиту") { values, _ -> values.sorted() }
        private val REGIONS = listOf("Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific")
        private val EXTRA = setOf("UTC", "Etc/UTC")
        private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
        private val BLANKS = Regex("\\s+")
        private const val SECONDS_IN_HOUR = 3600
        private const val SECONDS_IN_MINUTE = 60

        private val POPULAR_ZONES = listOf(
            "UTC", "Europe/London", "Europe/Berlin", "Europe/Kyiv", "Europe/Minsk", "Europe/Moscow", "Europe/Istanbul", "Asia/Dubai",
            "Asia/Tashkent", "Asia/Almaty", "Asia/Kolkata", "Asia/Bangkok", "Asia/Shanghai", "Asia/Tokyo", "Australia/Sydney",
            "Pacific/Auckland", "America/Sao_Paulo", "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
        )

        private val BY_OFFSET = ChoiceOrder("по смещению UTC") { values, at ->
            val offsets = values.associateWith { offsetSeconds(it, at) }
            values.sortedWith(compareBy({ offsets.getValue(it) }, { it }))
        }

        /**
         * Time zones a person would look for: `Europe/Moscow`, `America/New_York` and `UTC`. Legacy aliases (`US/Pacific`,
         * `SystemV/AST4`) and the `Etc/GMT+N` series are left out; they only make the list longer. Each zone tells the time
         * there now and its offset from UTC; the order is by that offset, then by name, or by alphabet.
         */
        val ZONES: ChoiceSource by lazy {
            val ids = ZoneId.getAvailableZoneIds().filter { id -> id in EXTRA || REGIONS.any { id.startsWith("$it/") } }.sorted()
            ChoiceSource(ZONE_ID, ids, listOf(BY_OFFSET, ALPHABET), POPULAR_ZONES) { zone, at ->
                listOf("Смещение" to offsetText(offsetSeconds(zone, at)), "Сейчас там" to localTime(zone, at))
            }
        }

        /** The source named [id], or `null` when there is none. */
        fun named(id: String): ChoiceSource? = if (id == ZONE_ID) ZONES else null

        /** The offset of [zone] from UTC at the moment [at], in seconds, summer time included. */
        fun offsetSeconds(zone: String, at: Instant): Int = ZoneId.of(zone).rules.getOffset(at).totalSeconds

        /** An offset as `UTC`, `UTC+03:00` or `UTC-05:00`. */
        fun offsetText(seconds: Int): String {
            if (seconds == 0) return "UTC"
            val sign = if (seconds < 0) "-" else "+"
            val minutes = abs(seconds) % SECONDS_IN_HOUR / SECONDS_IN_MINUTE
            return String.format(Locale.ROOT, "UTC%s%02d:%02d", sign, abs(seconds) / SECONDS_IN_HOUR, minutes)
        }

        /** The time of day in [zone] at the moment [at], as `21:45`. */
        fun localTime(zone: String, at: Instant): String = at.atZone(ZoneId.of(zone)).format(CLOCK)

        /**
         * Whether [value] contains [query], ignoring the case and the difference between `_`, `/` and a space, so that
         * `new york` finds `America/New_York`.
         */
        fun matches(value: String, query: String): Boolean = normalize(value).contains(normalize(query))

        private fun normalize(text: String): String =
            text.lowercase(Locale.ROOT).replace('_', ' ').replace('/', ' ').trim().replace(BLANKS, " ")
    }
}
