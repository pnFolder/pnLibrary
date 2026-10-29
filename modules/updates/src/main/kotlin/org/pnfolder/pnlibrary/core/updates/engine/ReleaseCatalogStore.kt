package org.pnfolder.pnlibrary.core.updates.engine

import org.pnfolder.pnlibrary.common.io.AtomicFiles
import org.pnfolder.pnlibrary.common.io.Digests
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

/**
 * Validated cached catalog entry.
 *
 * @property bytes encoded catalog bytes
 * @property fresh whether the cache age is within the requested TTL
 */
internal data class CatalogCacheHit(val bytes: ByteArray, val fresh: Boolean)

/** Digest-protected, atomic on-disk cache for release catalogs. */
internal class ReleaseCatalogStore(private val root: Path) {
    private val lock = Any()

    /** Reads and verifies the cached catalog for [uri], when present. */
    fun read(uri: URI, ttl: Duration): CatalogCacheHit? = synchronized(lock) {
        val data = dataPath(uri)
        val digest = digestPath(uri)
        if (!Files.isRegularFile(data) || !Files.isRegularFile(digest)) return null
        return try {
            val bytes = Files.readAllBytes(data)
            val expected = String(Files.readAllBytes(digest), StandardCharsets.US_ASCII).trim()
            if (Digests.sha256(bytes) != expected) {
                quarantine(uri)
                null
            } else {
                val age = Duration.ofMillis((System.currentTimeMillis() - Files.getLastModifiedTime(data).toMillis()).coerceAtLeast(0))
                CatalogCacheHit(bytes, age <= ttl)
            }
        } catch (_: Exception) {
            // An unreadable cache entry is treated as a miss and moved aside; the caller refetches it.
            quarantine(uri)
            null
        }
    }

    /** Atomically writes catalog [bytes] and their digest for [uri]. */
    fun write(uri: URI, bytes: ByteArray) = synchronized(lock) {
        val data = dataPath(uri)
        Files.createDirectories(data.parent)
        AtomicFiles.write(data, bytes)
        AtomicFiles.write(digestPath(uri), Digests.sha256(bytes).toByteArray(StandardCharsets.US_ASCII))
    }

    /** Moves corrupt cache files for [uri] aside for later diagnosis. */
    fun quarantine(uri: URI) = synchronized(lock) {
        val suffix = ".corrupt-${System.currentTimeMillis()}"
        // A file that cannot be moved stays in place and fails verification again on the next read.
        listOf(dataPath(uri), digestPath(uri)).forEach { path ->
            if (Files.exists(path)) runCatchingNonFatal {
                Files.move(path, path.resolveSibling(path.fileName.toString() + suffix), StandardCopyOption.REPLACE_EXISTING)
            }
            pruneQuarantine(path)
        }
    }

    /** Keeps the newest [KEPT_CORRUPT] quarantined copies of [path] so a flapping catalog cannot fill the disk. */
    private fun pruneQuarantine(path: Path) = runCatchingNonFatal {
        val prefix = path.fileName.toString() + ".corrupt-"
        Files.newDirectoryStream(root) { it.fileName.toString().startsWith(prefix) }.use { stream ->
            stream.sortedByDescending { it.fileName.toString().removePrefix(prefix).toLongOrNull() ?: 0L }
                .drop(KEPT_CORRUPT).forEach { runCatchingNonFatal { Files.deleteIfExists(it) } }
        }
    }

    internal fun dataPath(uri: URI): Path = root.resolve(key(uri) + ".data")
    private fun digestPath(uri: URI): Path = root.resolve(key(uri) + ".sha256")

    private companion object {
        const val KEPT_CORRUPT = 3
    }

    private fun key(uri: URI) = Digests.sha256(uri.toASCIIString().toByteArray(StandardCharsets.UTF_8))
}
