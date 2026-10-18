package ru.privatenull.pnlibrary.core.updates

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.core.updates.engine.ArtifactVerificationException
import ru.privatenull.pnlibrary.core.updates.engine.ArtifactVerifier
import ru.privatenull.pnlibrary.core.updates.engine.TransactionJournal
import ru.privatenull.pnlibrary.core.updates.engine.TrustedHttpClient
import ru.privatenull.pnlibrary.core.updates.engine.UpdateTransaction
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

class UpdatePlanStagerTest {
    @TempDir lateinit var directory: Path
    private val bytes = byteArrayOf(1, 2, 3)
    private val product = ProductId.of("pnlibrary")
    private val version = SemanticVersion.parse("2.0.0")
    private var downloads = 0

    @Test
    fun `published digest and size are checked before an update is exposed`() {
        assertThrows(ArtifactVerificationException::class.java) { stage("00".repeat(32), bytes.size.toLong()) }
        assertFalse(Files.exists(directory.resolve("plugins/update/library.jar")))
        assertArrayEquals(byteArrayOf(9), Files.readAllBytes(directory.resolve("plugins/library.jar")))
        assertThrows(ArtifactVerificationException::class.java) { stage(digest(), 100) }
        assertFalse(Files.exists(directory.resolve("plugins/update/library.jar")))
    }

    @Test
    fun `missing required checksum is rejected before making a download request`() {
        assertThrows(IllegalArgumentException::class.java) { stage(null, null) }
        assertEquals(0, downloads)
    }

    @Test
    fun `matching release metadata stages bytes while leaving the running artifact alone`() {
        stage(digest(), bytes.size.toLong())
        assertArrayEquals(bytes, Files.readAllBytes(directory.resolve("plugins/update/library.jar")))
        assertArrayEquals(byteArrayOf(9), Files.readAllBytes(directory.resolve("plugins/library.jar")))
    }

    @Test
    fun `saved transaction identifies the backup version time and originating plan`() {
        val before = Instant.now()
        stage(digest(), bytes.size.toLong())
        val root = directory.resolve("plugins/pnLibrary/updates/transactions")
        val path = Files.list(root).use { it.findFirst().get().resolve("journal.json") }
        val journal = TransactionJournal.load(path)
        assertFalse(Instant.parse(journal.createdAt).isBefore(before))
        assertNotNull(UUID.fromString(journal.planId))
        assertEquals("1.0.0", journal.artifacts.single().previousVersion)
        assertEquals("2.0.0", journal.artifacts.single().expectedVersion)
    }

    private fun stage(hash: String?, size: Long?) {
        val plugins = directory.resolve("plugins")
        val data = plugins.resolve("pnLibrary")
        Files.createDirectories(data)
        val current = plugins.resolve("library.jar")
        Files.write(current, byteArrayOf(9))
        val http = object : TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            override fun get(uri: URI, maximumBytes: Int): ByteArray { downloads++; return bytes }
        }
        val artifact = ArtifactDescriptor("library-new.jar", PlatformType.BUKKIT, 8, null,
            size, hash, URI("https://github.com/example/repo/releases/download/v2/library-new.jar"))
        val release = ProductRelease(product, version, UpdateChannel.STABLE, ApiVersionRange(1, 1), artifacts = listOf(artifact))
        val plan = UpdatePlan(1, listOf(ProductChange(product, SemanticVersion.parse("1.0.0"), version)), listOf(release))
        val transaction = UpdateTransaction(data.resolve("updates/transactions"), ArtifactVerifier(1024), plugins)
        UpdatePlanStager(PlatformType.BUKKIT, data, http, transaction, 1024).stage(UUID.randomUUID(), plan,
            mapOf(product to InstalledUpdateTarget(current, plugins.resolve("update"))))
    }

    private fun digest() = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
