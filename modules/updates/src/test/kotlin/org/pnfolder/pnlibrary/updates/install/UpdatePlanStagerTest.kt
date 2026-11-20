package org.pnfolder.pnlibrary.updates.install

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.updates.*
import org.pnfolder.pnlibrary.api.updates.product.ProductId
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.release.*
import org.pnfolder.pnlibrary.api.version.ApiVersionRange
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactVerificationException
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactVerifier
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionJournal
import org.pnfolder.pnlibrary.updates.engine.transaction.UpdateTransaction
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths
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
        val root = directory.resolve("plugins/pnLibrary/data/updates/transactions")
        val path = Files.list(root).use { it.findFirst().get().resolve("journal.json") }
        val journal = TransactionJournal.load(path)
        assertFalse(Instant.parse(journal.createdAt).isBefore(before))
        assertNotNull(UUID.fromString(journal.planId))
        assertEquals("1.0.0", journal.artifacts.single().previousVersion)
        assertEquals("2.0.0", journal.artifacts.single().expectedVersion)
    }

    @Test
    fun `refuses to download from a host outside the trusted list`() {
        val error = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            stage(digest(), bytes.size.toLong(), "https://evil.example.com/library-new.jar")
        }
        org.junit.jupiter.api.Assertions.assertTrue(error.message!!.contains("evil.example.com"))
        assertEquals(0, downloads)
    }

    @Test
    fun `the Java build the administrator chose wins, then the one that matches the installed product`() {
        assertEquals("build-11.jar", downloadedBuild(choice = 11, installed = 8))
        assertEquals("build-8.jar", downloadedBuild(choice = 8, installed = 11))
        assertEquals("build-8.jar", downloadedBuild(choice = null, installed = 8))
        assertEquals("build-11.jar", downloadedBuild(choice = null, installed = 11))
        // nothing chosen and nothing known: the old behaviour, the first build that fits
        assertEquals("build-8.jar", downloadedBuild(choice = null, installed = null))
        // a choice no build fits is ignored in favour of the installed one
        assertEquals("build-11.jar", downloadedBuild(choice = 99, installed = 11))
    }

    private fun downloadedBuild(choice: Int?, installed: Int?): String {
        val root = java.nio.file.Files.createTempDirectory(directory, "choice")
        val plugins = root.resolve("plugins")
        val data = plugins.resolve("pnLibrary")
        Files.createDirectories(data)
        val current = plugins.resolve("library.jar")
        Files.write(current, byteArrayOf(9))
        var downloaded = ""
        val http = object : TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            override fun <T> stream(uri: URI, maximumBytes: Long, reader: (java.io.InputStream) -> T): T {
                downloaded = uri.path.substringAfterLast('/')
                return reader(java.io.ByteArrayInputStream(bytes))
            }
        }
        val artifacts = listOf(8, 11).map { java ->
            ArtifactDescriptor("build-$java.jar", PlatformType.BUKKIT, java, null, bytes.size.toLong(), digest(),
                URI("https://github.com/example/repo/releases/download/v2/build-$java.jar"))
        }
        val release = ProductRelease(product, version, UpdateChannel.STABLE, ApiVersionRange(1, 1), artifacts = artifacts)
        val plan = UpdatePlan(1, listOf(ProductChange(product, SemanticVersion.parse("1.0.0"), version)), listOf(release))
        val transaction = UpdateTransaction(LibraryPaths(data).transactions, ArtifactVerifier(1024), plugins)
        UpdatePlanStager(PlatformType.BUKKIT, LibraryPaths(data), http, transaction, 1024, true, { choice }, { installed }).stage(
            UUID.randomUUID(), plan, mapOf(product to InstalledUpdateTarget(current, plugins.resolve("update"))),
        )
        return downloaded
    }

    private fun stage(hash: String?, size: Long?, url: String = "https://github.com/example/repo/releases/download/v2/library-new.jar") {
        val plugins = directory.resolve("plugins")
        val data = plugins.resolve("pnLibrary")
        Files.createDirectories(data)
        val current = plugins.resolve("library.jar")
        Files.write(current, byteArrayOf(9))
        val http = object : TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            override fun <T> stream(uri: URI, maximumBytes: Long, reader: (java.io.InputStream) -> T): T {
                downloads++
                return reader(java.io.ByteArrayInputStream(bytes))
            }
        }
        val artifact = ArtifactDescriptor("library-new.jar", PlatformType.BUKKIT, 8, null,
            size, hash, URI(url))
        val release = ProductRelease(product, version, UpdateChannel.STABLE, ApiVersionRange(1, 1), artifacts = listOf(artifact))
        val plan = UpdatePlan(1, listOf(ProductChange(product, SemanticVersion.parse("1.0.0"), version)), listOf(release))
        val transaction = UpdateTransaction(LibraryPaths(data).transactions, ArtifactVerifier(1024), plugins)
        UpdatePlanStager(PlatformType.BUKKIT, LibraryPaths(data), http, transaction, 1024).stage(UUID.randomUUID(), plan,
            mapOf(product to InstalledUpdateTarget(current, plugins.resolve("update"))))
    }

    private fun digest() = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
