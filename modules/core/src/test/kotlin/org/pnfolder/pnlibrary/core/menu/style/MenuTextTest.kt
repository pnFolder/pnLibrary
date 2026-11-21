package org.pnfolder.pnlibrary.core.menu.style

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class MenuTextTest {
    @Test
    fun `time left picks the two largest units and never shows zero minutes`() {
        assertEquals("1 мин", timeLeft(Duration.ofSeconds(5)))
        assertEquals("2 ч 10 мин", timeLeft(Duration.ofMinutes(130)))
        assertEquals("3 д 4 ч", timeLeft(Duration.ofHours(76)))
    }

    @Test
    fun `ago speaks of the past and the future`() {
        val now = Instant.now()

        assertEquals("только что", ago(now.minusSeconds(10)))
        assertEquals("5 мин назад", ago(now.minus(Duration.ofMinutes(5)).minusSeconds(5)))
        assertTrue(ago(now.plus(Duration.ofHours(3))).startsWith("через "))
    }

    @Test
    fun `progress bar is never empty and never longer than the widest bar`() {
        val widest = progressBar(100, 100).length

        assertEquals(1, progressBar(0, 100).length)
        assertEquals(widest, progressBar(500, 100).length)
        assertEquals(1, progressBar(0, 0).length)
    }

    @Test
    fun `every menu permission is listed once`() {
        val declared = MenuPermissions::class.java.declaredFields
            .filter { it.type == String::class.java && java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.also { f -> f.isAccessible = true }.get(null) as String }

        assertEquals(declared.toSet(), MenuPermissions.ALL.toSet())
        assertEquals(MenuPermissions.ALL.size, MenuPermissions.ALL.toSet().size)
    }
}
