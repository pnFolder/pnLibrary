package ru.privatenull.pnlibrary.update

import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class ArtifactDownloadException(message: String) : IllegalStateException(message)

class ArtifactDownloader(private val maximumBytes: Long) {
    init { require(maximumBytes > 0) { "maximumBytes must be positive" } }

    fun download(openStream: () -> InputStream, destination: Path): Long {
        val absolute = destination.toAbsolutePath().normalize()
        val parent = absolute.parent ?: throw ArtifactDownloadException("download destination must have a parent")
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, absolute.fileName.toString(), ".part")
        try {
            var total = 0L
            openStream().use { input ->
                Files.newOutputStream(temporary).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maximumBytes) throw ArtifactDownloadException("download exceeds the configured size limit")
                        output.write(buffer, 0, count)
                    }
                }
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING)
            }
            return total
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
