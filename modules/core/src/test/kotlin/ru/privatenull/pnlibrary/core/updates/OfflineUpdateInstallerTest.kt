package ru.privatenull.pnlibrary.core.updates

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.updates.UpdateConfigurationBackup
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.core.updates.engine.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class OfflineUpdateInstallerTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `offline application installs and rolls back without repeating completed work`() {
        val fixture = prepare()
        assertArrayEquals(byteArrayOf(1), Files.readAllBytes(fixture.active))
        assertEquals(1, OfflineUpdateInstaller.apply(fixture.data))
        assertArrayEquals(byteArrayOf(2, 3), Files.readAllBytes(fixture.active))
        assertFalse(Files.exists(fixture.pending))
        assertEquals(0, OfflineUpdateInstaller.apply(fixture.data))
        assertEquals(1, fixture.transactions.awaitingHealth().size)
        fixture.transactions.completeHealth(fixture.journal, true)
        assertEquals(1, fixture.transactions.rollbackCandidates().size)
        fixture.transactions.rollback(fixture.journal)
        assertEquals(1, OfflineUpdateInstaller.apply(fixture.data))
        assertArrayEquals(byteArrayOf(1), Files.readAllBytes(fixture.active))
        assertEquals(0, OfflineUpdateInstaller.apply(fixture.data))
    }

    @Test
    fun `active runtime lock prevents any offline changes`() {
        val fixture = prepare()
        UpdateInstallationLock.acquire(fixture.data).use {
            assertThrows(IllegalStateException::class.java) { OfflineUpdateInstaller.apply(fixture.data) }
        }
        assertArrayEquals(byteArrayOf(1), Files.readAllBytes(fixture.active))
        assertEquals(1, OfflineUpdateInstaller.apply(fixture.data))
    }

    @Test
    fun `corrupt preserved payload is rejected before replacing an installed jar`() {
        val fixture = prepare()
        val journal = TransactionJournal.load(fixture.journal)
        Files.write(Path.of(journal.artifacts.single().staged), byteArrayOf(7))
        assertThrows(ArtifactVerificationException::class.java) { OfflineUpdateInstaller.apply(fixture.data) }
        assertArrayEquals(byteArrayOf(1), Files.readAllBytes(fixture.active))
    }

    @Test
    fun `runtime recovery refuses to rewrite installed jars after interrupted offline publication`() {
        val fixture = prepare()
        val source = directory.resolve("interrupted.jar").also { Files.write(it, byteArrayOf(8)) }
        val specification = ArtifactSpecification(ProductId.of("pnlibrary"), SemanticVersion.parse("3.0.0"),
            ApiVersionRange(8, 8), source.fileName.toString())
        val result = fixture.transactions.prepareForRestart(listOf(TransactionArtifact(specification, source, fixture.active)),
            purpose = "INSTALL", parentTransaction = java.util.UUID.fromString(TransactionJournal.load(fixture.journal).id))
        val journal = TransactionJournal.load(result.journal)
        TransactionJournal.save(result.journal, journal.copy(state = TransactionState.PUBLISHING))
        assertThrows(IllegalArgumentException::class.java) { fixture.transactions.recoverAll() }
        assertArrayEquals(byteArrayOf(8), Files.readAllBytes(fixture.active))
        fixture.transactions.recoverAll(offline = true)
        assertArrayEquals(byteArrayOf(1), Files.readAllBytes(fixture.active))
    }

    @Test
    fun `failure after configuration replacement restores both previous jars and previous settings`() {
        val fixture = prepare(configuration = true)
        OfflineUpdateInstaller.apply(fixture.data)
        val config = fixture.data.resolve("config.yml")
        Files.writeString(config, "version: 2")
        fixture.transactions.completeHealth(fixture.journal, true)
        fixture.transactions.rollback(fixture.journal, restoreConfigurations = true)
        // A collision in the platform update directory is discovered during final consumption.
        Files.write(fixture.pending, byteArrayOf(99))
        assertThrows(ArtifactVerificationException::class.java) { OfflineUpdateInstaller.apply(fixture.data) }
        assertArrayEquals(byteArrayOf(2, 3), Files.readAllBytes(fixture.active))
        assertEquals("version: 2", Files.readString(config))
        assertArrayEquals(byteArrayOf(99), Files.readAllBytes(fixture.pending))
    }

    private data class Fixture(val data: Path, val active: Path, val pending: Path, val journal: Path, val transactions: UpdateTransaction)

    private fun prepare(configuration: Boolean = false): Fixture {
        val plugins = directory.resolve("plugins")
        val data = plugins.resolve("pnLibrary").also(Files::createDirectories)
        val active = plugins.resolve("library.jar").also { Files.write(it, byteArrayOf(1)) }
        val pending = plugins.resolve("update/library.jar")
        val bytes = byteArrayOf(2, 3)
        val source = directory.resolve("release.jar").also { Files.write(it, bytes) }
        val api = ApiVersionRange(8, 8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val specification = ArtifactSpecification(ProductId.of("pnlibrary"), SemanticVersion.parse("2.0.0"), api, "release.jar", bytes.size.toLong(), digest)
        val transactions = UpdateTransaction(data.resolve("updates/transactions"), ArtifactVerifier(1024), plugins)
        val plan = java.util.UUID.randomUUID()
        val snapshot = if (configuration) {
            Files.writeString(data.resolve("config.yml"), "version: 1")
            ConfigurationSnapshots(data.resolve("updates/configurations"), plugins).capture(plan,
                mapOf(ProductId.of("pnlibrary") to UpdateConfigurationBackup(data, listOf("config.yml"))))
        } else null
        val result = transactions.prepareForRestart(listOf(TransactionArtifact(specification, source, pending, active,
            "1.0.0", api, active)), planId = plan, targetApi = 8, configurationSnapshot = snapshot)
        return Fixture(data, active, pending, result.journal, transactions)
    }
}
