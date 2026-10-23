package org.pnfolder.pnlibrary.core.updates.engine

import org.pnfolder.pnlibrary.common.io.AtomicFiles
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/** Raised when an artifact cannot be downloaded safely. */
internal class ArtifactDownloadException(message: String) : IllegalStateException(message)

/** Streams artifacts into atomic temporary files under a strict byte limit. */
internal class ArtifactDownloader(private val maximumBytes: Long) {
    init { require(maximumBytes > 0) { "maximumBytes must be positive" } }

    /** Downloads into [destination] and returns the number of bytes written. */
    fun download(openStream: () -> InputStream, destination: Path): Long {
        val absolute = destination.toAbsolutePath().normalize()
        val parent = absolute.parent ?: throw ArtifactDownloadException("Download destination must have a parent")
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
                        if (total > maximumBytes) throw ArtifactDownloadException("Download exceeds the configured size limit")
                        output.write(buffer, 0, count)
                    }
                }
            }
            AtomicFiles.move(temporary, absolute)
            return total
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
