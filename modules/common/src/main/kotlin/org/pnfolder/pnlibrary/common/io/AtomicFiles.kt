package org.pnfolder.pnlibrary.common.io

import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * File replacement that readers never observe half-written.
 *
 * Content is written to a temporary file beside the target and then moved over it. File systems that cannot move
 * atomically get an ordinary move instead, which is still a single rename on every local file system.
 */
object AtomicFiles {
    /**
     * Moves [source] onto [target], atomically when the file system supports it.
     *
     * @param replaceExisting whether an existing [target] is replaced; otherwise the move fails if it exists
     * @throws FileAlreadyExistsException when [target] exists and [replaceExisting] is false; checked up front because
     *   an atomic move may replace the target regardless of options on some platforms, Windows among them
     */
    @JvmStatic
    @JvmOverloads
    fun move(source: Path, target: Path, replaceExisting: Boolean = true) {
        if (!replaceExisting && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw FileAlreadyExistsException(target.toString())
        val options = if (replaceExisting) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray()
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, *options)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, *options)
        }
    }

    /**
     * Replaces [target] with [bytes], creating parent directories as needed.
     *
     * @param durable whether the content is flushed to the storage device before it replaces [target], for state
     *   that must survive a power loss
     */
    @JvmStatic
    @JvmOverloads
    fun write(target: Path, bytes: ByteArray, durable: Boolean = false) {
        write(target, durable) { temporary -> Files.write(temporary, bytes) }
    }

    /**
     * Replaces [target] with what [fill] writes to the temporary file it is given, creating parent directories as
     * needed. The temporary file is removed whether or not [fill] succeeds.
     */
    @JvmStatic
    @JvmOverloads
    fun write(target: Path, durable: Boolean = false, fill: (Path) -> Unit) {
        val parent = target.toAbsolutePath().parent
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, target.fileName.toString(), ".tmp")
        try {
            fill(temporary)
            if (durable) FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
            move(temporary, target)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
