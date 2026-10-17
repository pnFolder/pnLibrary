package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.update.UpdatePaths
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean

/** Prevents the runtime and the offline installer from mutating the same update directory together. */
internal class UpdateInstallationLock private constructor(private val channel: FileChannel, private val lock: FileLock) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try { lock.release() } finally { channel.close() }
    }

    companion object {
        fun acquire(dataDirectory: Path): UpdateInstallationLock {
            val path = UpdatePaths.inside(dataDirectory.resolve("updates/install.lock"), dataDirectory)
            Files.createDirectories(path.parent)
            val channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            try {
                val lock = channel.tryLock() ?: error("pnLibrary is active or another installer is running")
                return UpdateInstallationLock(channel, lock)
            } catch (error: Exception) {
                channel.close()
                throw IllegalStateException("Cannot acquire update lock. Stop the server before applying updates.", error)
            }
        }
    }
}
