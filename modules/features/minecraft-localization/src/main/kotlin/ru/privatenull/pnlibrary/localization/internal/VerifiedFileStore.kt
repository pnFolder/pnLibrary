package ru.privatenull.pnlibrary.localization.internal

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal class VerifiedFileStore(private val root: Path) {
    fun translation(version: String, locale: String): Path = root.resolve("translations").resolve(version).resolve("$locale.json")
    fun manifest(): Path = root.resolve("version-manifest.json")

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
}
