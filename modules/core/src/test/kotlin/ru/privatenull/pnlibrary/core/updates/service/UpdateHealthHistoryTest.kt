package ru.privatenull.pnlibrary.core.updates.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.core.updates.engine.*
import ru.privatenull.pnlibrary.core.updates.history.UpdateEventJournal
import ru.privatenull.pnlibrary.core.updates.history.UpdateStateStore
import ru.privatenull.pnlibrary.core.updates.install.UpdateInstaller
import ru.privatenull.pnlibrary.core.updates.policy.UpdateConfiguration
import ru.privatenull.pnlibrary.spi.platform.PlatformIdentity
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

class UpdateHealthHistoryTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `health history uses the original transaction rather than the latest resolver snapshot`() {
        val fixture = fixture()
        val unrelated = UUID.randomUUID()
        val state = UpdateStateStore(fixture.data.resolve("updates"))
        state.save(UpdatePlanSnapshot(unrelated, 99, UpdateState.UPDATE_AVAILABLE, null, emptyList(), "newer check"))
        val events = UpdateEventJournal(fixture.data.resolve("updates/events"))
        fixture.installer.reconcileVersions(mapOf("pnlibrary" to "2.0.0"), { events.recordHealth(it) })
        val event = events.read().single()
        assertEquals(fixture.plan, event.planId)
        assertEquals(listOf(UpdateEventJournal.Change("pnlibrary", "1.0.0", "2.0.0")), event.changes)
        assertEquals("HEALTH", event.operation)
        assertEquals(unrelated, state.current()?.id)
        assertEquals(TransactionState.COMMITTED, TransactionJournal.load(fixture.journal).state)
    }

    @Test
    fun `rollback is confirmed once with the actual restored version transition`() {
        val fixture = fixture()
        fixture.installer.reconcileVersions(mapOf("pnlibrary" to "2.0.0"), {})
        fixture.transactions.rollback(fixture.journal)
        val rollback = TransactionJournal.load(fixture.journal)
        TransactionJournal.save(fixture.journal, rollback.copy(rollbackSession = UUID.randomUUID().toString()))
        val reports = mutableListOf<UpdateHealthReport>()
        fixture.installer.reconcileVersions(mapOf("pnlibrary" to "1.0.0"), reports::add)
        assertTrue(reports.single().healthy)
        assertTrue(reports.single().rollback)
        assertEquals("2.0.0", reports.single().changes.single().from.toString())
        assertEquals("1.0.0", reports.single().changes.single().to.toString())
        fixture.installer.reconcileVersions(mapOf("pnlibrary" to "1.0.0"), reports::add)
        assertEquals(1, reports.size)
        assertTrue(TransactionJournal.load(fixture.journal).rollbackHealthVerified)
    }

    @Test
    fun `failed verification freezes automatic updates until matching versions are observed`() {
        val fixture = fixture()
        val reports = mutableListOf<UpdateHealthReport>()
        fixture.installer.reconcileVersions(mapOf("pnlibrary" to "1.0.0"), reports::add)
        assertFalse(reports.single().healthy)
        assertFalse(fixture.installer.automaticUpdatesHealthy(setOf(ProductId.of("pnlibrary"))))
        fixture.installer.reconcileVersions(mapOf("pnlibrary" to "2.0.0"), reports::add, recoveredFailuresOnly = true)
        assertTrue(reports.last().healthy)
        assertTrue(fixture.installer.automaticUpdatesHealthy(setOf(ProductId.of("pnlibrary"))))
    }

    @Test
    fun `a preparation in this process is not mistaken for a successful restart`() {
        val fixture = fixture(previousProcess = false)
        val reports = mutableListOf<UpdateHealthReport>()
        fixture.installer.reconcileVersions(mapOf("pnlibrary" to "2.0.0"), reports::add)
        assertTrue(reports.isEmpty())
        assertEquals(TransactionState.AWAITING_HEALTH, TransactionJournal.load(fixture.journal).state)
    }

    @Test
    fun `failed audit persistence does not silently acknowledge a verified installation`() {
        val fixture = fixture()
        assertThrows(IllegalStateException::class.java) {
            fixture.installer.reconcileVersions(mapOf("pnlibrary" to "2.0.0"), { error("audit disk unavailable") })
        }
        assertEquals(TransactionState.AWAITING_HEALTH, TransactionJournal.load(fixture.journal).state)
        assertFalse(fixture.installer.automaticUpdatesHealthy(setOf(ProductId.of("pnlibrary"))))
        fixture.installer.reconcileVersions(mapOf("pnlibrary" to "2.0.0"), {})
        assertEquals(TransactionState.COMMITTED, TransactionJournal.load(fixture.journal).state)
    }

    private data class Fixture(val data: Path, val plan: UUID, val journal: Path, val transactions: UpdateTransaction, val installer: UpdateInstaller)

    private fun fixture(previousProcess: Boolean = true): Fixture {
        val plugins = directory.resolve("plugins")
        val data = plugins.resolve("pnLibrary").also(Files::createDirectories)
        val installed = plugins.resolve("library.jar").also { Files.write(it, byteArrayOf(1)) }
        val source = directory.resolve("release.jar").also { Files.write(it, byteArrayOf(2)) }
        val api = ApiVersionRange(8, 8)
        val specification = ArtifactSpecification(ProductId.of("pnlibrary"), SemanticVersion.parse("2.0.0"), api, "release.jar")
        val transactions = UpdateTransaction(data.resolve("updates/transactions"), ArtifactVerifier(1024), plugins)
        val plan = UUID.randomUUID()
        val result = transactions.prepareForRestart(listOf(TransactionArtifact(specification, source,
            plugins.resolve("update/library.jar"), installed, "1.0.0", api, installed)), planId = plan, targetApi = 8)
        if (previousProcess) {
            val journal = TransactionJournal.load(result.journal)
            TransactionJournal.save(result.journal, journal.copy(preparedSession = UUID.randomUUID().toString()))
        }
        val platform = object : PlatformIdentity { override val type = PlatformType.BUKKIT }
        val http = object : TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            override fun get(uri: URI, maximumBytes: Int): ByteArray = error("health verification must not access the network")
        }
        return Fixture(data, plan, result.journal, transactions, UpdateInstaller(platform, data, UpdateConfiguration(), http))
    }
}
