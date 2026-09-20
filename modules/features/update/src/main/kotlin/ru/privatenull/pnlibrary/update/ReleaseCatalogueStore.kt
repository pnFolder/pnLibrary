package ru.privatenull.pnlibrary.update

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration

data class CatalogueCacheHit(val bytes: ByteArray, val fresh: Boolean)

class ReleaseCatalogueStore(private val root: Path) {
    private val lock = Any()

    fun read(uri: URI, ttl: Duration): CatalogueCacheHit? = synchronized(lock) {
        val data = dataPath(uri)
        val digest = digestPath(uri)
        if (!Files.isRegularFile(data) || !Files.isRegularFile(digest)) return null
        return try {
            val bytes = Files.readAllBytes(data)
            val expected = String(Files.readAllBytes(digest), StandardCharsets.US_ASCII).trim()
            if (sha256(bytes) != expected) {
                quarantine(uri)
                null
            } else {
                val age = Duration.ofMillis((System.currentTimeMillis() - Files.getLastModifiedTime(data).toMillis()).coerceAtLeast(0))
                CatalogueCacheHit(bytes, age <= ttl)
            }
        } catch (_: Exception) {
            quarantine(uri)
            null
        }
    }

    fun write(uri: URI, bytes: ByteArray) = synchronized(lock) {
        val data = dataPath(uri)
        Files.createDirectories(data.parent)
        atomicWrite(data, bytes)
        atomicWrite(digestPath(uri), sha256(bytes).toByteArray(StandardCharsets.US_ASCII))
    }

    fun quarantine(uri: URI) = synchronized(lock) {
        val suffix = ".corrupt-${System.currentTimeMillis()}"
        listOf(dataPath(uri), digestPath(uri)).forEach { path ->
            if (Files.exists(path)) runCatching {
                Files.move(path, path.resolveSibling(path.fileName.toString() + suffix), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    internal fun dataPath(uri: URI): Path = root.resolve(key(uri) + ".data")
    private fun digestPath(uri: URI): Path = root.resolve(key(uri) + ".sha256")
    internal fun rewriteDigest(uri: URI) = synchronized(lock) {
        val bytes = Files.readAllBytes(dataPath(uri))
        atomicWrite(digestPath(uri), sha256(bytes).toByteArray(StandardCharsets.US_ASCII))
    }

    private fun key(uri: URI) = sha256(uri.toASCIIString().toByteArray(StandardCharsets.UTF_8))
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    private fun atomicWrite(path: Path, bytes: ByteArray) {
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, path.fileName.toString(), ".tmp")
        try {
            Files.write(temporary, bytes)
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
