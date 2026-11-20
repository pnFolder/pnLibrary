package org.pnfolder.pnlibrary.updates.service

import org.pnfolder.pnlibrary.updates.engine.UpdatePaths
import org.pnfolder.pnlibrary.updates.layout.LibraryPaths
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
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
        /** Takes the lock of [paths], failing if another process holds it. */
        fun acquire(paths: LibraryPaths): UpdateInstallationLock {
            val path = UpdatePaths.inside(paths.installLock, paths.data)
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
