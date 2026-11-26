package org.pnfolder.pnlibrary.updates.service;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.updates.engine.UpdatePaths;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

/** Prevents the runtime and the offline installer from mutating the same update directory together. */
public final class UpdateInstallationLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private UpdateInstallationLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /** Takes the lock of {@code paths}, failing if another process holds it. */
    @NotNull
    public static UpdateInstallationLock acquire(@NotNull LibraryPaths paths) throws IOException {
        Path path = UpdatePaths.inside(paths.getInstallLock(), paths.getData());
        Files.createDirectories(path.getParent());
        FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                throw new IllegalStateException("pnLibrary is active or another installer is running");
            }
            return new UpdateInstallationLock(channel, lock);
        } catch (Exception error) {
            channel.close();
            throw new IllegalStateException("Cannot acquire update lock. Stop the server before applying updates.", error);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            lock.release();
        } catch (IOException error) {
            throw new java.io.UncheckedIOException(error);
        } finally {
            try {
                channel.close();
            } catch (IOException error) {
                throw new java.io.UncheckedIOException(error);
            }
        }
    }
}
