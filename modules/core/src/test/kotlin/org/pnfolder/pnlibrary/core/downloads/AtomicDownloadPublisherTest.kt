package org.pnfolder.pnlibrary.core.downloads

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.common.io.AtomicFiles
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class AtomicDownloadPublisherTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `existing directory is preserved when a download targets it`() {
        val target = directory.resolve("models")
        Files.createDirectories(target)
        val original = target.resolve("existing.bin")
        Files.write(original, "original".toByteArray())
        val staging = file("staging.bin", "download")

        assertThrows(IllegalArgumentException::class.java) {
            AtomicDownloadPublisher(directory).publish(listOf(PreparedDownload(staging, target)))
        }
        assertArrayEquals("original".toByteArray(), Files.readAllBytes(original))
        assertFalse(Files.exists(staging))
    }

    @Test
    fun `publication failure restores originals and removes newly installed files`() {
        val existing = file("existing.bin", "original")
        val newTarget = directory.resolve("new.bin")
        val downloads = listOf(
            PreparedDownload(file("first.tmp", "replacement"), existing),
            PreparedDownload(file("second.tmp", "new"), newTarget),
            PreparedDownload(directory.resolve("missing.tmp"), directory.resolve("last.bin")),
        )

        assertThrows(IOException::class.java) { AtomicDownloadPublisher(directory).publish(downloads) }
        assertArrayEquals("original".toByteArray(), Files.readAllBytes(existing))
        assertFalse(Files.exists(newTarget))
        downloads.forEach { assertFalse(Files.exists(it.staging)) }
    }

    @Test
    fun `failed restoration preserves backups and still restores other files`() {
        val first = file("first.bin", "original first")
        val second = file("second.bin", "original second")
        val publicationFailure = IOException("Publication failed")
        val restorationFailure = IOException("Restoration failed")
        val publisher = AtomicDownloadPublisher(directory) { source, target ->
            when {
                source.fileName.toString() == "missing.tmp" -> throw publicationFailure
                source.parent.parent.fileName.toString() == "transactions" && target == second -> throw restorationFailure
                else -> AtomicFiles.move(source, target)
            }
        }

        assertSame(publicationFailure, assertThrows(IOException::class.java) {
            publisher.publish(listOf(
                PreparedDownload(file("first.tmp", "new first"), first),
                PreparedDownload(file("second.tmp", "new second"), second),
                PreparedDownload(directory.resolve("missing.tmp"), directory.resolve("last.bin")),
            ))
        })
        assertTrue(publicationFailure.suppressed.contains(restorationFailure))
        assertArrayEquals("original first".toByteArray(), Files.readAllBytes(first))
        val backups = Files.walk(directory.resolve("downloads/transactions")).use { paths ->
            paths.filter(Files::isRegularFile).toArray().map { it as Path }
        }
        assertTrue(backups.any { String(Files.readAllBytes(it)) == "original second" })
    }

    private fun file(name: String, content: String): Path = Files.write(directory.resolve(name), content.toByteArray())
}
