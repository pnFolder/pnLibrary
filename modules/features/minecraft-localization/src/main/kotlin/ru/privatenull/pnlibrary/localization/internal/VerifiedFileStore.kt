package ru.privatenull.pnlibrary.localization.internal

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal class VerifiedFileStore(private val root: Path) {
    fun translation(version: String, locale: String): Path = root.resolve("translations").resolve(version).resolve("$locale.json")
    fun manifest(): Path = root.resolve("version-manifest.json")
    fun versionMetadata(version: String): Path = root.resolve("versions").resolve("$version.json")
    fun assetIndex(version: String): Path = root.resolve("asset-indexes").resolve("$version.json")
    fun temporary(suffix: String): Path {
        val directory = root.resolve("temporary")
        Files.createDirectories(directory)
        return Files.createTempFile(directory, "download-", suffix)
    }

    fun read(path: Path): ByteArray? = if (Files.isRegularFile(path)) Files.readAllBytes(path) else null

    fun write(path: Path, bytes: ByteArray) {
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

    fun quarantine(path: Path) {
        if (!Files.exists(path)) return
        Files.move(path, path.resolveSibling("${path.fileName}.corrupt-${System.currentTimeMillis()}"), StandardCopyOption.REPLACE_EXISTING)
    }

    fun sha1(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    fun sha1(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-1")
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
