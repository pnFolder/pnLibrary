package org.pnfolder.pnlibrary.updates.engine.transaction;

import org.pnfolder.pnlibrary.common.io.AtomicFiles;
import org.pnfolder.pnlibrary.common.lang.Failures;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Puts files in place so that a crash never leaves a half-written jar. */
final class AtomicPublication {
    private static final int LOCK_ATTEMPTS = 5;
    private static final long LOCK_RETRY_MILLIS = 200L;

    /** A file-system step that may be refused while another process holds the file. */
    interface FileAction {
        void run() throws IOException;
    }

    /** Copies {@code source} beside {@code target} first and then swaps it in. */
    void replace(Path source, Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path publishing = publishingCopy(target);
        try {
            Files.copy(source, publishing, StandardCopyOption.REPLACE_EXISTING);
            retryWhileLocked(() -> AtomicFiles.move(publishing, target));
        } catch (Throwable error) {
            Failures.runCatchingNonFatal(() -> Files.deleteIfExists(publishing)).onFailure(error::addSuppressed);
            throw error;
        }
    }

    /** The copy that is about to be swapped in; a crash during publication can leave it behind. */
    Path publishingCopy(Path target) {
        return target.resolveSibling(target.getFileName() + ".publishing");
    }

    /**
     * Runs {@code action}, trying again a few times when the file system refuses it. On Windows a jar that antivirus or the
     * previous JVM still holds open cannot be replaced for a moment, and failing the whole transaction for that is
     * worse than waiting a second.
     */
    void retryWhileLocked(FileAction action) throws IOException {
        int attempt = 1;
        while (true) {
            try {
                action.run();
                return;
            } catch (FileSystemException error) {
                if (attempt >= LOCK_ATTEMPTS) {
                    throw error;
                }
                attempt++;
                pause();
            }
        }
    }

    private static void pause() throws IOException {
        try {
            Thread.sleep(LOCK_RETRY_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            InterruptedIOException error = new InterruptedIOException("Interrupted while waiting for a locked file");
            error.initCause(interrupted);
            throw error;
        }
    }
}
