package org.pnfolder.pnlibrary.updates.paths

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.updates.engine.FreezeStore
import org.pnfolder.pnlibrary.updates.engine.UpdatePreferencesStore
import org.pnfolder.pnlibrary.updates.policy.UpdatePolicyOverrides
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LegacyPathsMigrationTest {
    @TempDir
    lateinit var directory: Path

    private val plugin get() = directory.resolve("plugins/pnLibrary")
    private val paths get() = LibraryPaths(plugin)
    private val warnings = mutableListOf<String>()

    private fun write(relative: String, text: String): Path =
        plugin.resolve(relative).also { Files.createDirectories(it.parent); Files.writeString(it, text) }

    private fun migrate() = LegacyPathsMigration(paths, warnings::add).run()

    @Test
    fun `old state is moved into data and the old places are gone`() {
        write("updates/components.json", "components")
        write("updates/transactions/one/journal.json", "transaction")
        write("journal/day.log", "journal")
        write("reports/report.zip", "report")
        write("observability/observations.jsonl", "observations")
        write("diagnostics/history/incident.json", "incident")
        write("task-history.tsv", "tasks")
        write("event-history.tsv", "events")
        write("upload-ledger.json", "ledger")
        write("downloads/pins.state", "pins")
        write("config.yml", "keep")

        migrate()

        assertEquals("components", Files.readString(paths.components))
        assertEquals("transaction", Files.readString(paths.transactions.resolve("one/journal.json")))
        assertEquals("journal", Files.readString(paths.journal.resolve("day.log")))
        assertEquals("report", Files.readString(paths.reports.resolve("report.zip")))
        assertEquals("observations", Files.readString(paths.observations))
        assertEquals("incident", Files.readString(paths.diagnosticsHistory.resolve("incident.json")))
        assertEquals("tasks", Files.readString(paths.taskHistory))
        assertEquals("events", Files.readString(paths.eventHistory))
        assertEquals("ledger", Files.readString(paths.uploadLedger))
        assertEquals("pins", Files.readString(paths.downloads.resolve("pins.state")))
        listOf("updates", "journal", "reports", "observability", "diagnostics", "task-history.tsv", "event-history.tsv", "upload-ledger.json", "downloads")
            .forEach { assertFalse(Files.exists(plugin.resolve(it)), it) }
        assertEquals("keep", Files.readString(paths.legacyConfig))
        assertTrue(warnings.isEmpty(), warnings.toString())
    }

    @Test
    fun `an existing new location is never overwritten`() {
        write("task-history.tsv", "old")
        write("data/history/task-history.tsv", "new")
        write("updates/components.json", "old")
        write("data/updates/components.json", "new")
        write("updates/catalog/cache.json", "old catalog")

        migrate()

        assertEquals("new", Files.readString(paths.taskHistory))
        assertEquals("old", Files.readString(plugin.resolve("task-history.tsv")))
        assertEquals("new", Files.readString(paths.components))
        assertEquals("old", Files.readString(plugin.resolve("updates/components.json")))
        assertEquals("old catalog", Files.readString(paths.catalog.resolve("cache.json")))
    }

    @Test
    fun `a fresh install and a repeated run change nothing`() {
        migrate()
        assertFalse(Files.exists(paths.data))
        write("task-history.tsv", "tasks")
        migrate()
        migrate()
        assertEquals("tasks", Files.readString(paths.taskHistory))
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `the two old settings files become one preferences file and are removed`() {
        write("updates/settings.json", """{"allPlugins":true,"channels":{"shop":"BETA"},"automatic":{"shop":false}}""")
        write("updates/freezes.json", """{"shop":${Instant.parse("2026-09-17T12:00:00Z").toEpochMilli()}}""")

        migrate()

        assertFalse(Files.exists(paths.updates.resolve("settings.json")))
        assertFalse(Files.exists(paths.updates.resolve("freezes.json")))
        val store = UpdatePreferencesStore(paths.preferences)
        val overrides = UpdatePolicyOverrides(store)
        assertEquals(UpdateChannel.BETA, overrides.channel("shop"))
        assertEquals(false, overrides.automatic("shop"))
        assertEquals(true, overrides.allPlugins())
        val clock = Clock.fixed(Instant.parse("2026-09-17T10:00:00Z"), ZoneOffset.UTC)
        assertEquals(setOf(ProductId.of("shop")), FreezeStore(store, clock).active().keys)
    }

    @Test
    fun `an unreadable old settings file is kept and the other one is still merged`() {
        write("updates/settings.json", "{ broken")
        write("updates/freezes.json", """{"shop":4102444800000}""")

        migrate()

        assertTrue(Files.exists(paths.updates.resolve("settings.json")))
        assertFalse(Files.exists(paths.updates.resolve("freezes.json")))
        assertEquals(mapOf("shop" to 4102444800000L), UpdatePreferencesStore(paths.preferences).current().freezes)
        assertEquals(1, warnings.size, warnings.toString())
    }

    @Test
    fun `old settings do not replace a preferences file that already exists`() {
        write("data/updates/preferences.json", """{"allPlugins":false}""")
        write("data/updates/settings.json", """{"allPlugins":true}""")

        migrate()

        assertEquals(false, UpdatePreferencesStore(paths.preferences).current().allPlugins)
    }
}
