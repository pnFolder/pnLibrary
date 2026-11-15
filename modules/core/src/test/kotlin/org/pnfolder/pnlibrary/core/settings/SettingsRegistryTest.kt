package org.pnfolder.pnlibrary.core.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration

class SettingsRegistryTest {
    private val fields = LibrarySettings::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.map { it.name }

    @Test
    fun `every setting of the library has exactly one entry in the registry`() {
        assertTrue(fields.isNotEmpty())
        val covered = SettingsRegistry.all.map { it.field }
        assertEquals(fields.sorted(), covered.sorted(), "a field without an entry (or an entry without a field)")
        assertEquals(covered.size, covered.toSet().size, "two entries for one field")
        assertEquals(SettingsRegistry.all.size, SettingsRegistry.all.map { it.key }.toSet().size, "a key is used twice")
    }

    @Test
    fun `the default of every setting is accepted by that setting`() {
        SettingsRegistry.all.forEach { spec ->
            assertEquals(spec.default, spec.normalize(spec.default), spec.key)
            assertEquals(LibrarySettings(), spec.apply(LibrarySettings(), spec.default), spec.key)
            assertTrue(spec.description.isNotBlank() && spec.title.isNotBlank(), spec.key)
        }
        assertEquals(LibrarySettings().toConfig().historyRetentionDays, 30)
    }

    @Test
    fun `the docs describe every setting and every permission of plugin yml`() {
        val docs = Files.readString(Paths.get("../../docs/settings.md"))
        val missing = SettingsRegistry.all.map { it.key }.filterNot { "`$it`" in docs }
        assertTrue(missing.isEmpty(), "docs/settings.md does not describe: $missing")
        val pluginYml = Paths.get("../../platforms/bukkit/runtime/src/main/resources/plugin.yml")
        val declared = Files.readAllLines(pluginYml).mapNotNull { Regex("""^  (pnlibrary\.[a-z.]+):\s*$""").find(it)?.groupValues?.get(1) }
        assertTrue(declared.size >= 10, "plugin.yml permissions were not found")
        val unlisted = declared.filterNot { it in docs }
        assertTrue(unlisted.isEmpty(), "docs/settings.md does not mention: $unlisted")
    }

    @Test
    fun `switches read the usual words and refuse the rest`() {
        val spec = requireNotNull(SettingsRegistry["privacy"])
        assertEquals("true", spec.normalize("ON"))
        assertEquals("false", spec.normalize(" нет "))
        assertThrows(SettingException::class.java) { spec.normalize("maybe") }
    }

    @Test
    fun `numbers stay inside their limits`() {
        val spec = requireNotNull(SettingsRegistry["history-retention-days"])
        assertEquals("365", spec.normalize("365"))
        assertTrue(assertThrows(SettingException::class.java) { spec.normalize("366") }.message.orEmpty().contains("от 30 до 365"))
        assertThrows(SettingException::class.java) { spec.normalize("30.5") }
        assertThrows(SettingException::class.java) { spec.normalize("") }
    }

    @Test
    fun `times are typed with units in either language and written in the shortest form`() {
        val spec = requireNotNull(SettingsRegistry["updates.check-interval"])
        assertEquals("1ч30м", spec.normalize("90m"))
        assertEquals("1ч30м", spec.normalize("1h 30 мин"))
        assertEquals("6ч", spec.normalize("6h"))
        assertEquals(Duration.ofHours(12), spec.apply(LibrarySettings(), "12h").updateCheckInterval)
        assertThrows(SettingException::class.java) { spec.normalize("5m") }
        assertThrows(SettingException::class.java) { spec.normalize("8d") }
        assertThrows(SettingException::class.java) { spec.normalize("6") }
    }

    @Test
    fun `a time zone and a message are checked`() {
        val zone = requireNotNull(SettingsRegistry["time-zone"])
        assertEquals("Europe/Moscow", zone.normalize(" Europe/Moscow "))
        assertThrows(SettingException::class.java) { zone.normalize("Mars/Base") }
        val message = requireNotNull(SettingsRegistry["no-permission-message"])
        assertThrows(SettingException::class.java) { message.normalize("   ") }
        assertThrows(SettingException::class.java) { message.normalize("я".repeat(257)) }
    }
}
