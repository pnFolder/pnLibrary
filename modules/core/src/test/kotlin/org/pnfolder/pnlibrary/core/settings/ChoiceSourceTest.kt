package org.pnfolder.pnlibrary.core.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class ChoiceSourceTest {
    private val zones = ChoiceSource.ZONES
    private val winter = Instant.parse("2026-01-15T12:00:00Z")
    private val summer = Instant.parse("2026-07-15T12:00:00Z")

    @Test
    fun `zones are sorted, unique and contain the ones people look for`() {
        assertEquals(zones.all.sorted(), zones.all)
        assertEquals(zones.all.size, zones.all.toSet().size)
        listOf("Europe/Moscow", "America/New_York", "Asia/Tokyo", "UTC", "Etc/UTC").forEach { assertTrue(it in zones.all, it) }
    }

    @Test
    fun `legacy aliases and the GMT series are left out`() {
        assertFalse(zones.all.any { it.startsWith("SystemV/") || it.startsWith("Etc/GMT") || it.startsWith("US/") })
        assertTrue(zones.all.all { runCatching { ZoneId.of(it) }.isSuccess })
    }

    @Test
    fun `the default order covers every zone by offset and then by name`() {
        val shown = zones.view(0, false, "", winter)

        assertEquals(zones.all.toSet(), shown.toSet())
        assertEquals(zones.all.size, shown.size)
        assertEquals(shown.sortedWith(compareBy({ ChoiceSource.offsetSeconds(it, winter) }, { it })), shown)
        val plusThree = shown.filter { ChoiceSource.offsetSeconds(it, winter) == 3 * 3600 }
        assertTrue("Europe/Moscow" in plusThree && "Africa/Nairobi" in plusThree && plusThree == plusThree.sorted())
    }

    @Test
    fun `the offset order follows summer time and the alphabet order does not`() {
        val berlinWinter = ChoiceSource.offsetSeconds("Europe/Berlin", winter)
        val berlinSummer = ChoiceSource.offsetSeconds("Europe/Berlin", summer)

        assertEquals(3600, berlinWinter)
        assertEquals(7200, berlinSummer)
        assertEquals(zones.all, zones.view(1, false, "", winter))
        assertEquals(listOf("по смещению UTC", "по алфавиту"), zones.orders.map { it.label })
    }

    @Test
    fun `popular zones are valid ids in the given order`() {
        val wanted = listOf(
            "UTC", "Europe/London", "Europe/Berlin", "Europe/Kyiv", "Europe/Minsk", "Europe/Moscow", "Europe/Istanbul", "Asia/Dubai",
            "Asia/Tashkent", "Asia/Almaty", "Asia/Kolkata", "Asia/Bangkok", "Asia/Shanghai", "Asia/Tokyo", "Australia/Sydney",
            "Pacific/Auckland", "America/Sao_Paulo", "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
        )

        assertEquals(wanted.filter { it in zones.all }, zones.popular)
        assertEquals(zones.popular, zones.view(0, true, "", winter))
        assertTrue(zones.popular.all { it in zones.all })
        assertEquals("UTC", zones.popular.first())
        val custom = ChoiceSource("x", listOf("a", "b"), curated = listOf("b", "missing", "a"))
        assertEquals(listOf("b", "a"), custom.popular)
    }

    @Test
    fun `a filter ignores case and the difference between underscore, slash and space`() {
        assertEquals(listOf("America/New_York"), zones.view(0, false, "new york", winter))
        assertEquals(listOf("America/New_York"), zones.view(0, false, "NEW_YORK", winter))
        assertEquals(listOf("America/New_York"), zones.view(0, false, "america/new_york", winter))
        assertTrue(zones.view(0, false, "moscow", winter) == listOf("Europe/Moscow"))
        assertTrue(zones.view(0, false, "europe/", winter).all { it.startsWith("Europe/") })
        assertTrue(zones.view(0, false, "no such zone anywhere", winter).isEmpty())
        assertEquals(zones.view(0, false, "", winter), zones.view(0, false, "   ", winter))
        assertEquals(zones.view(0, false, "york", winter), zones.view(0, true, "york", winter), "a filter searches everything")
    }

    @Test
    fun `offsets are written as UTC, UTC+03 00 and UTC-05 00`() {
        assertEquals("UTC", ChoiceSource.offsetText(0))
        assertEquals("UTC+03:00", ChoiceSource.offsetText(3 * 3600))
        assertEquals("UTC-05:00", ChoiceSource.offsetText(-5 * 3600))
        assertEquals("UTC+05:30", ChoiceSource.offsetText(5 * 3600 + 1800))
        assertEquals("UTC-09:30", ChoiceSource.offsetText(-(9 * 3600 + 1800)))
    }

    @Test
    fun `a zone tells the time there and its current offset`() {
        assertEquals(listOf("Смещение" to "UTC+03:00", "Сейчас там" to "15:00"), zones.lore("Europe/Moscow", winter))
        assertEquals(listOf("Смещение" to "UTC-05:00", "Сейчас там" to "07:00"), zones.lore("America/New_York", winter))
        assertEquals(listOf("Смещение" to "UTC-04:00", "Сейчас там" to "08:00"), zones.lore("America/New_York", summer))
        assertEquals(listOf("Смещение" to "UTC", "Сейчас там" to "12:00"), zones.lore("UTC", winter))
        assertTrue(ChoiceSource("x", listOf("a")).lore("a").isEmpty())
    }

    @Test
    fun `the source is found again by the name that travels over the link`() {
        val spec = requireNotNull(SettingsRegistry["time-zone"])

        assertEquals(zones, ChoiceSource.named(zones.id))
        assertNull(ChoiceSource.named("nope"))
        assertEquals(zones.id, spec.choices?.id)
        assertEquals(SettingKind.CHOICE, spec.kind)
    }

    @Test
    fun `the cycle of zones is every zone and steps wrap around`() {
        val all = zones.all

        assertEquals(all, zones.cycle)
        assertEquals(all[1], zones.next(all[0]))
        assertEquals(all.last(), zones.previous(all[0]))
        assertEquals(all.first(), zones.next(all.last()))
    }

    @Test
    fun `a zone outside the cycle comes first in the rolling list and steps to the ends`() {
        assertEquals(listOf("US/Pacific") + zones.cycle, zones.rolling("US/Pacific"))
        assertEquals(zones.cycle, zones.rolling("Europe/Moscow"))
        assertEquals(zones.cycle.first(), zones.next("US/Pacific"))
        assertEquals(zones.cycle.last(), zones.previous("US/Pacific"))
    }

    @Test
    fun `a source without a cycle rolls through all its values`() {
        val source = ChoiceSource("letters", listOf("a", "b", "c"))

        assertEquals(listOf("a", "b", "c"), source.cycle)
        assertEquals("a", source.next("c"))
        assertEquals("c", source.previous("a"))
    }
}
