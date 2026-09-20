package ru.privatenull.pnlibrary.update

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.updates.ComponentId
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
    fun `verifier rejects checksum and embedded identity mismatches`() {
        val jar = artifact("market.jar", "market", "2.0.0", 4, byteArrayOf(1, 2, 3))
        val valid = specification(jar, "market", "2.0.0", 4)
        ArtifactVerifier(1024 * 1024).verify(jar, valid)

        assertThrows(ArtifactVerificationException::class.java) {
            ArtifactVerifier(1024 * 1024).verify(jar, valid.copy(sha256 = "00".repeat(32)))
        }
        assertThrows(ArtifactVerificationException::class.java) {
            ArtifactVerifier(1024 * 1024).verify(jar, valid.copy(component = ComponentId.of("auth")))
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
    fun `failed health check restores the complete previous set`() {
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

        assertEquals(TransactionState.ROLLED_BACK, result.state)
        assertArrayEquals(byteArrayOf(9), Files.readAllBytes(marketTarget))
        assertArrayEquals(byteArrayOf(8), Files.readAllBytes(authTarget))
    }

    @Test
    fun `restart recovery rolls back an interrupted activation`() {
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
                listOf(JournalArtifact("market", target.toString(), staged.toString(), backup.toString(), true)),
            ),
        )

        val transaction = UpdateTransaction(transactionRoot, ArtifactVerifier(1024))
        val result = transaction.recover(journalPath)

        assertEquals(TransactionState.ROLLED_BACK, result.state)
        assertArrayEquals(byteArrayOf(1), Files.readAllBytes(target))
        assertEquals(TransactionState.ROLLED_BACK, TransactionJournal.load(journalPath).state)
        assertEquals(TransactionState.ROLLED_BACK, transaction.recoverAll().single().state)
    }

    private fun specification(path: Path, id: String, version: String, api: Int) = ArtifactSpecification(
        component = ComponentId.of(id),
        version = SemanticVersion.parse(version),
        supportedApi = ApiVersionRange(api, api),
        fileName = path.fileName.toString(),
        size = Files.size(path),
        sha256 = sha256(path),
    )

    private fun artifact(name: String, id: String, version: String, api: Int, payload: ByteArray): Path {
        val path = directory.resolve(name)
        JarOutputStream(Files.newOutputStream(path)).use { output ->
            output.putNextEntry(JarEntry(EmbeddedDescriptorReader.ENTRY))
            output.write(ComponentDescriptorCodec().encodeInstalled(
                ru.privatenull.pnlibrary.api.updates.ComponentDescriptor.builder(id, version)
                    .pnLibraryApi(api, api).build(),
            ))
            output.closeEntry()
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
