package org.pnfolder.pnlibrary.core.menu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.config.ConfigSetting
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind
import java.util.concurrent.TimeUnit

class ConfigNumbersTest {
    private fun number(value: String, step: Double = 1.0, unit: String = "", duration: TimeUnit? = null, min: Double? = null, max: Double? = null) =
        ConfigSetting("p", "Число", "", ConfigSettingKind.INTEGER, value, min = min, max = max, step = step, unit = unit, duration = duration)

    @Test
    fun `a time is written out in words`() {
        assertEquals("5400 сек (1 ч 30 мин)", ConfigNumbers.shown(number("5400", duration = TimeUnit.SECONDS)))
        assertEquals("0 с", ConfigNumbers.human(0))
    }

    @Test
    fun `steps of seconds go through minutes to an hour`() {
        val labels = ConfigNumbers.ladder(number("30", duration = TimeUnit.SECONDS)).map { it.first }

        assertEquals(listOf("1 с", "1 мин", "10 мин", "1 ч"), labels)
        assertEquals(60.0, ConfigNumbers.ladder(number("30", duration = TimeUnit.SECONDS))[1].second)
    }

    @Test
    fun `a plain number steps by its step, ten times, a hundred and a thousand`() {
        val ladder = ConfigNumbers.ladder(number("10", step = 5.0, unit = "блоков"))

        assertEquals(listOf("5 блоков", "50 блоков", "500 блоков", "5000 блоков"), ladder.map { it.first })
    }

    @Test
    fun `steps wider than the bounds are dropped but two always remain`() {
        val ladder = ConfigNumbers.ladder(number("5", min = 0.0, max = 20.0))

        assertTrue(ladder.size >= 2)
        assertTrue(ladder.all { it.second <= 20.0 || it.second == 10.0 })
    }
}
