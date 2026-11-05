package org.pnfolder.pnlibrary.core.updates.engine

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.backup.UpdateConfigurationBackup
import org.pnfolder.pnlibrary.api.version.ApiVersionRange
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import org.pnfolder.pnlibrary.core.updates.history.ConfigurationSnapshots
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

class UpdateTransactionFailureAfterPublishTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `failure after the first jar was replaced restores it and keeps the configuration backup`() {
        val plugins = directory.resolve("plugins").also(Files::createDirectories)
        val data = plugins.resolve("pnLibrary").also(Files::createDirectories)
        val first = plugins.resolve("first.jar").also { Files.write(it, byteArrayOf(1)) }
        // A non-empty folder where the temporary copy goes makes publishing the second jar fail after the first is in place.
        val second = plugins.resolve("second.jar")
        Files.createDirectories(plugins.resolve("second.jar.publishing/blocker"))
        Files.writeString(data.resolve("config.yml"), "version: 1")
        val plan = UUID.randomUUID()
        val snapshot = ConfigurationSnapshots(data.resolve("updates/configurations"), plugins).capture(plan,
            mapOf(ProductId.of("first") to UpdateConfigurationBackup(data, listOf("config.yml"))))
        val firstSource = release("first-new.jar", byteArrayOf(2, 2))
        val secondSource = release("second-new.jar", byteArrayOf(3, 3))
        val transactions = UpdateTransaction(data.resolve("updates/transactions"), ArtifactVerifier(1024), plugins)

        assertThrows(Exception::class.java) {
            transactions.prepareForRestart(listOf(
                TransactionArtifact(spec("first", firstSource), firstSource, first),
                TransactionArtifact(spec("second", secondSource), secondSource, second),
            ), planId = plan, configurationSnapshot = snapshot)
        }

        assertArrayEquals(byteArrayOf(1), Files.readAllBytes(first))
        assertFalse(Files.exists(second))
        assertEquals("version: 1", Files.readString(data.resolve("config.yml")))
        assertTrue(Files.isDirectory(data.resolve("updates/configurations")))
        assertTrue(transactions.awaitingHealth().isEmpty())
    }

    private fun release(name: String, bytes: ByteArray): Path = directory.resolve(name).also { Files.write(it, bytes) }

    private fun spec(id: String, path: Path): ArtifactSpecification {
        val bytes = Files.readAllBytes(path)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return ArtifactSpecification(ProductId.of(id), SemanticVersion.parse("2.0.0"), ApiVersionRange(8, 8),
            path.fileName.toString(), bytes.size.toLong(), digest)
    }
}
