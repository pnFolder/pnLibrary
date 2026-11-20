package org.pnfolder.pnlibrary.core.settings

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class SettingsStoreTest {
    @TempDir lateinit var directory: Path

    private val warnings = mutableListOf<String>()
    private val file: Path get() = directory.resolve("data/settings.json")

    private fun store() = SettingsStore(file) { warnings += it }

    @Test
    fun `a new server has the defaults and no file`() {
        val store = store()

        assertEquals(LibrarySettings(), store.current())
        assertFalse(Files.exists(file))
        assertTrue(store.values().none { it.changed })
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `a value survives a restart and only changed values are written`() {
        val first = store()
        first.set("privacy", "true")
        first.set("updates.check-interval", "90m")
        first.set("downloads.destinations.cache", "off")

        val second = store()

        assertTrue(second.current().privacy)
        assertEquals(Duration.ofMinutes(90), second.current().updateCheckInterval)
        assertFalse(second.current().downloadsToCache)
        assertEquals(setOf("privacy", "updates.check-interval", "downloads.destinations.cache"), second.values().filter { it.changed }.map { it.spec.key }.toSet())
        val json = JsonParser.parseString(Files.readString(file)).asJsonObject
        assertEquals(setOf("privacy", "updates.check-interval", "downloads.destinations.cache"), json.keySet())
        assertTrue(json.get("privacy").asJsonPrimitive.isBoolean)
        assertEquals("1ч30м", json.get("updates.check-interval").asString)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `reset returns a setting to its default and removes it from the file`() {
        val store = store()
        store.set("history-retention-days", "90")

        val change = store.reset("history-retention-days")

        assertEquals("90", change.before)
        assertEquals("30", change.after)
        assertEquals(30, store.current().historyRetentionDays)
        assertFalse(JsonParser.parseString(Files.readString(file)).asJsonObject.has("history-retention-days"))
    }

    @Test
    fun `an invalid value is refused and nothing changes`() {
        val store = store()
        store.set("history-retention-days", "60")

        assertThrows(SettingException::class.java) { store.set("history-retention-days", "7") }
        assertThrows(SettingException::class.java) { store.set("no-such-key", "1") }
        assertThrows(SettingException::class.java) { store.reset("no-such-key") }

        assertEquals(60, store().current().historyRetentionDays)
    }

    @Test
    fun `invalid values in the file fall back to the defaults with a warning`() {
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            """{"history-retention-days": 7, "privacy": "maybe", "time-zone": "Mars/Base", "mystery": 1, "journal-foreign-plugins": true, "downloads.enabled": [1]}""",
        )

        val store = store()

        assertEquals(30, store.current().historyRetentionDays)
        assertFalse(store.current().privacy)
        assertEquals(LibrarySettings().timeZone, store.current().timeZone)
        assertTrue(store.current().downloadsEnabled)
        assertTrue(store.current().journalForeignPlugins, "a valid value next to broken ones is kept")
        assertEquals(5, warnings.size, warnings.toString())
        assertTrue(warnings.any { it.contains("history-retention-days") })
        assertTrue(warnings.any { it.contains("mystery") })
    }

    @Test
    fun `a file that is not json is moved aside and the defaults are used`() {
        Files.createDirectories(file.parent)
        Files.writeString(file, "{ not json")

        val store = store()

        assertEquals(LibrarySettings(), store.current())
        assertTrue(Files.exists(directory.resolve("data/settings.json.broken")))
        assertEquals(1, warnings.size)
        store.set("privacy", "true")
        assertTrue(store().current().privacy)
    }

    @Test
    fun `listeners hear about a change and the config follows it`() {
        val store = store()
        val heard = mutableListOf<String>()
        store.onChange { heard += it.timeZone }

        store.set("time-zone", "Europe/Moscow")

        assertEquals(listOf("Europe/Moscow"), heard)
        assertEquals("Europe/Moscow", store.config().timeZone)
        store.set("privacy", "true")
        assertTrue(store.config().privacy)
    }

    @Test
    fun `a failing listener does not undo a saved change`() {
        val store = store()
        store.onChange { error("boom") }

        store.set("privacy", "true")

        assertTrue(store.current().privacy)
        assertTrue(warnings.single().contains("privacy"))
    }
}
