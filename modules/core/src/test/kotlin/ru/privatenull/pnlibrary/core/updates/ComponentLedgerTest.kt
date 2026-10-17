package ru.privatenull.pnlibrary.core.updates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ComponentLedgerTest {
    @TempDir
    lateinit var directory: Path

    private val clock = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC)
    private val warnings = mutableListOf<String>()

    private val journal by lazy { UpdateEventJournal(directory.resolve("events"), clock) }
    private val ledger by lazy { ComponentLedger(directory.resolve("components.json"), journal, warnings::add) }

    private fun component(version: String, channel: String = "stable", automatic: Boolean = false) =
        ComponentLedger.Component(version, channel, automatic)

    private fun history() = journal.read(pageSize = 100).map { it.operation to it.changes.single().product }

    @Test
    fun `the first observation is only a baseline and writes no history`() {
        ledger.observe(mapOf("pnlibrary" to component("2.0.0"), "currency" to component("1.0.0")))
        assertEquals(0, journal.count())
        assertTrue(Files.isRegularFile(directory.resolve("components.json")))
    }

    @Test
    fun `new, removed and manually changed components are recorded`() {
        ledger.observe(mapOf("pnlibrary" to component("2.0.0"), "currency" to component("1.0.0")))
        ledger.observe(mapOf("pnlibrary" to component("2.1.0"), "shop" to component("0.5.0")))

        val recorded = history().toSet()
        assertEquals(
            setOf(
                ComponentLedger.OPERATION_ADDED to "shop",
                ComponentLedger.OPERATION_REMOVED to "currency",
                ComponentLedger.OPERATION_VERSION_CHANGED to "pnlibrary",
            ),
            recorded,
        )
        val change = journal.read(product = "pnlibrary").single().changes.single()
        assertEquals("2.0.0", change.from)
        assertEquals("2.1.0", change.to)
    }

    @Test
    fun `a version change already verified by a restart health check is not reported twice`() {
        ledger.observe(mapOf("pnlibrary" to component("2.0.0")))
        ledger.observe(mapOf("pnlibrary" to component("2.1.0")), verifiedByHealth = setOf("pnlibrary"))
        assertEquals(0, journal.count())
    }

    @Test
    fun `channel and automatic download changes are described in the event message`() {
        ledger.observe(mapOf("pnlibrary" to component("2.0.0", "stable", false)))
        ledger.observe(mapOf("pnlibrary" to component("2.0.0", "beta", true)))

        val event = journal.read().single()
        assertEquals(ComponentLedger.OPERATION_SETTINGS_CHANGED, event.operation)
        assertEquals("канал: stable → beta; автозагрузка: выкл → вкл", event.message)
    }

    @Test
    fun `an unchanged start records nothing and the state survives reopening`() {
        ledger.observe(mapOf("pnlibrary" to component("2.0.0")))
        val reopened = ComponentLedger(directory.resolve("components.json"), journal, warnings::add)
        reopened.observe(mapOf("pnlibrary" to component("2.0.0")))
        assertEquals(0, journal.count())

        reopened.observe(mapOf("pnlibrary" to component("2.0.0"), "shop" to component("1.0.0")))
        assertEquals(1, journal.count())
    }

    @Test
    fun `an unreadable ledger is treated as a new baseline instead of failing`() {
        Files.writeString(directory.resolve("components.json"), "{ not json")
        ledger.observe(mapOf("pnlibrary" to component("2.0.0")))
        assertEquals(0, journal.count())
        assertEquals(1, warnings.size)
    }
}
