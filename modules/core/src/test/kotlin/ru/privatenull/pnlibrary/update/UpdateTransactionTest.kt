package ru.privatenull.pnlibrary.update
import org.junit.jupiter.api.Assertions.assertTrue

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.io.ByteArrayInputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class UpdateTransactionTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `verifier rejects checksum mismatches`() {
        val jar = artifact("market.jar", "market", "2.0.0", 4, byteArrayOf(1, 2, 3))
        val valid = specification(jar, "market", "2.0.0", 4)
        ArtifactVerifier(1024 * 1024).verify(jar, valid)

        assertThrows(ArtifactVerificationException::class.java) {
            ArtifactVerifier(1024 * 1024).verify(jar, valid.copy(sha256 = "00".repeat(32)))
        }
    }

    @Test
    fun `downloader bounds bytes and never exposes a partial destination`() {
        val destination = directory.resolve("download.jar")
        assertThrows(ArtifactDownloadException::class.java) {
            ArtifactDownloader(3).download({ ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }, destination)
        }
        assertEquals(false, Files.exists(destination))

        assertEquals(3, ArtifactDownloader(3).download({ ByteArrayInputStream(byteArrayOf(1, 2, 3)) }, destination))
        assertArrayEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(destination))
    }

    @Test
    fun `commits an atomic component set after health check`() {
        val targets = directory.resolve("plugins").also(Files::createDirectories)
        val marketTarget = targets.resolve("market.jar").also { Files.write(it, byteArrayOf(9)) }
        val authTarget = targets.resolve("auth.jar").also { Files.write(it, byteArrayOf(8)) }
        val market = artifact("market-new.jar", "market", "2.0.0", 4, byteArrayOf(1))
        val auth = artifact("auth-new.jar", "auth", "3.0.0", 4, byteArrayOf(2))
        val transaction = UpdateTransaction(directory.resolve("transactions"), ArtifactVerifier(1024 * 1024))

        val result = transaction.apply(
            listOf(
                TransactionArtifact(specification(market, "market", "2.0.0", 4), market, marketTarget),
                TransactionArtifact(specification(auth, "auth", "3.0.0", 4), auth, authTarget),
            ),
        ) { true }

        assertEquals(TransactionState.COMMITTED, result.state)
        assertArrayEquals(Files.readAllBytes(market), Files.readAllBytes(marketTarget))
        assertArrayEquals(Files.readAllBytes(auth), Files.readAllBytes(authTarget))
        assertEquals(TransactionState.COMMITTED, TransactionJournal.load(result.journal).state)
    }

    @Test
    fun `commits three verified components as one transaction`() {
        val targets = directory.resolve("plugins").also(Files::createDirectories)
        val artifacts = listOf("library", "market", "auth").mapIndexed { index, id ->
            val source = artifact("$id-new.jar", id, "2.0.0", 2, byteArrayOf(index.toByte()))
            TransactionArtifact(specification(source, id, "2.0.0", 2), source, targets.resolve("$id.jar"))
        }
        val result = UpdateTransaction(directory.resolve("transactions"), ArtifactVerifier(1024 * 1024)).apply(artifacts) { true }
        assertEquals(TransactionState.COMMITTED, result.state)
        artifacts.forEach { assertArrayEquals(Files.readAllBytes(it.source), Files.readAllBytes(it.target)) }
    }

    @Test
    fun `rejects publication target outside allowed root`() {
        val source = artifact("market-new.jar", "market", "2.0.0", 1, byteArrayOf(1))
        val transaction = UpdateTransaction(directory.resolve("transactions"), ArtifactVerifier(1024 * 1024))
        assertThrows(IllegalArgumentException::class.java) {
            transaction.apply(listOf(TransactionArtifact(
                specification(source, "market", "2.0.0", 1), source,
                directory.parent.resolve("escaped-market.jar"),
            ))) { true }
        }
    }

    @Test
    fun `failed health check waits for administrator before rollback`() {
        val targets = directory.resolve("plugins").also(Files::createDirectories)
        val marketTarget = targets.resolve("market.jar").also { Files.write(it, byteArrayOf(9)) }
        val authTarget = targets.resolve("auth.jar").also { Files.write(it, byteArrayOf(8)) }
        val market = artifact("market-new.jar", "market", "2.0.0", 4, byteArrayOf(1))
        val auth = artifact("auth-new.jar", "auth", "3.0.0", 4, byteArrayOf(2))

        val result = UpdateTransaction(directory.resolve("transactions"), ArtifactVerifier(1024 * 1024)).apply(
            listOf(
                TransactionArtifact(specification(market, "market", "2.0.0", 4), market, marketTarget),
                TransactionArtifact(specification(auth, "auth", "3.0.0", 4), auth, authTarget),
            ),
        ) { false }

        assertEquals(TransactionState.FAILED, result.state)
        assertArrayEquals(Files.readAllBytes(market), Files.readAllBytes(marketTarget))
        assertArrayEquals(Files.readAllBytes(auth), Files.readAllBytes(authTarget))

        val rolledBack = UpdateTransaction(directory.resolve("transactions"), ArtifactVerifier(1024 * 1024))
            .rollback(result.journal)
        assertEquals(TransactionState.ROLLED_BACK, rolledBack.state)
        assertArrayEquals(byteArrayOf(9), Files.readAllBytes(marketTarget))
        assertArrayEquals(byteArrayOf(8), Files.readAllBytes(authTarget))
    }

    @Test
    fun `rollback restores installed jar into platform update directory`() {
        val installed = directory.resolve("plugins/market.jar").also {
            Files.createDirectories(it.parent)
            Files.write(it, byteArrayOf(9, 8, 7))
        }
        val updateTarget = directory.resolve("plugins/update/market.jar")
        val replacement = artifact("market-new.jar", "market", "2.0.0", 4, byteArrayOf(1))
        val transaction = UpdateTransaction(directory.resolve("transactions"), ArtifactVerifier(1024 * 1024))

        val applied = transaction.apply(listOf(TransactionArtifact(
            specification(replacement, "market", "2.0.0", 4),
            replacement,
            updateTarget,
            rollbackSource = installed,
        ))) { true }
        Files.delete(updateTarget)

        val rolledBack = transaction.rollback(applied.journal)

        assertEquals(TransactionState.ROLLED_BACK, rolledBack.state)
        assertArrayEquals(byteArrayOf(9, 8, 7), Files.readAllBytes(updateTarget))
    }

    @Test
    fun `prepared update remains pending across restart until health is confirmed`() {
        val transactionRoot = directory.resolve("transactions")
        val transactionDirectory = transactionRoot.resolve("interrupted")
        val backupDirectory = transactionDirectory.resolve("backup").also(Files::createDirectories)
        val target = directory.resolve("market.jar").also { Files.write(it, byteArrayOf(2)) }
        val backup = backupDirectory.resolve("market.jar").also { Files.write(it, byteArrayOf(1)) }
        val staged = transactionDirectory.resolve("staged.jar").also { Files.write(it, byteArrayOf(2)) }
        val journalPath = transactionDirectory.resolve("journal.json")
        TransactionJournal.save(
            journalPath,
            TransactionJournal(
                "interrupted",
                TransactionState.AWAITING_HEALTH,
                listOf(JournalArtifact("market", target.toString(), staged.toString(), backup.toString(), true, "2.0.0")),
            ),
        )

        val transaction = UpdateTransaction(transactionRoot, ArtifactVerifier(1024))
        val result = transaction.recover(journalPath)

        assertEquals(TransactionState.AWAITING_HEALTH, result.state)
        assertArrayEquals(byteArrayOf(2), Files.readAllBytes(target))
        assertEquals(mapOf("market" to "2.0.0"), transaction.awaitingHealth().single().expectedVersions)
        assertEquals(TransactionState.AWAITING_HEALTH, transaction.recoverAll().single().state)
        assertEquals(TransactionState.COMMITTED, transaction.completeHealth(journalPath, true).state)
    }

    @Test
    fun `rollback candidate skips a newer failed transaction without backups`() {
        val target = directory.resolve("plugins/market.jar").also {
            Files.createDirectories(it.parent)
            Files.write(it, byteArrayOf(9))
        }
        val valid = artifact("valid.jar", "market", "2.0.0", 4, byteArrayOf(1))
        val transaction = UpdateTransaction(directory.resolve("transactions"), ArtifactVerifier(1024 * 1024))
        val committed = transaction.apply(listOf(TransactionArtifact(
            specification(valid, "market", "2.0.0", 4), valid, target,
        ))) { true }

        val invalid = artifact("invalid.jar", "other", "3.0.0", 4, byteArrayOf(2))
        assertThrows(ArtifactVerificationException::class.java) {
            transaction.apply(listOf(TransactionArtifact(
                specification(invalid, "market", "3.0.0", 4).copy(sha256 = "00".repeat(32)), invalid, target,
            ))) { true }
        }

        assertEquals(committed.journal, transaction.latestRollbackCandidate())
    }

    @Test
    fun `selected rollback restores that saved set rather than the latest one`() {
        val plugins = directory.resolve("plugins").also(Files::createDirectories)
        val target = plugins.resolve("market.jar").also { Files.write(it, byteArrayOf(9)) }
        val transaction = UpdateTransaction(directory.resolve("transactions"), ArtifactVerifier(1024 * 1024), plugins)
        val first = artifact("first.jar", "market", "2.0.0", 4, byteArrayOf(1))
        val saved = transaction.apply(listOf(TransactionArtifact(specification(first, "market", "2.0.0", 4), first, target))) { true }
        val second = artifact("second.jar", "market", "3.0.0", 4, byteArrayOf(2))
        transaction.apply(listOf(TransactionArtifact(specification(second, "market", "3.0.0", 4), second, target))) { true }
        assertEquals(2, transaction.rollbackCandidates().size)
        val id = java.util.UUID.fromString(TransactionJournal.load(saved.journal).id)
        assertEquals(saved.journal, transaction.rollbackCandidate(id))
        transaction.rollback(transaction.rollbackCandidate(id))
        assertArrayEquals(byteArrayOf(9), Files.readAllBytes(target))
        assertThrows(Exception::class.java) { transaction.rollbackCandidate(java.util.UUID.randomUUID()) }
    }

    @Test
    fun `tampered target is rejected before any rollback file is changed`() {
        val plugins = directory.resolve("plugins").also(Files::createDirectories)
        val targets = listOf("market", "auth").map { plugins.resolve("$it.jar").also { path -> Files.write(path, byteArrayOf(9)) } }
        val transaction = UpdateTransaction(directory.resolve("transactions"), ArtifactVerifier(1024 * 1024), plugins)
        val inputs = listOf("market", "auth").mapIndexed { index, name ->
            val source = artifact("$name-new.jar", name, "2.0.0", 4, byteArrayOf(index.toByte()))
            TransactionArtifact(specification(source, name, "2.0.0", 4), source, targets[index])
        }
        val saved = transaction.apply(inputs) { true }
        val outside = directory.resolve("outside.jar").also { Files.write(it, byteArrayOf(7)) }
        val journal = TransactionJournal.load(saved.journal)
        TransactionJournal.save(saved.journal, journal.copy(artifacts = listOf(journal.artifacts[0], journal.artifacts[1].copy(target = outside.toString()))))
        assertThrows(IllegalArgumentException::class.java) { transaction.rollback(saved.journal) }
        assertArrayEquals(Files.readAllBytes(inputs[0].source), Files.readAllBytes(targets[0]))
        assertArrayEquals(byteArrayOf(7), Files.readAllBytes(outside))
        assertTrue(transaction.rollbackCandidates().isEmpty())
    }

    private fun specification(path: Path, id: String, version: String, api: Int) = ArtifactSpecification(
        product = ProductId.of(id),
        version = SemanticVersion.parse(version),
        supportedApi = ApiVersionRange(api, api),
        fileName = path.fileName.toString(),
        size = Files.size(path),
        sha256 = sha256(path),
    )

    private fun artifact(name: String, id: String, version: String, api: Int, payload: ByteArray): Path {
        val path = directory.resolve(name)
        JarOutputStream(Files.newOutputStream(path)).use { output ->
            output.putNextEntry(JarEntry("payload.bin"))
            output.write(payload)
            output.closeEntry()
        }
        return path
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
