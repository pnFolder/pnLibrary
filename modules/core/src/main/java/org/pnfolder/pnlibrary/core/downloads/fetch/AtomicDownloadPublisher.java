package org.pnfolder.pnlibrary.core.downloads.fetch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;
import org.pnfolder.pnlibrary.core.downloads.CleanupSteps;

/** Publishes a prepared download batch as one recoverable filesystem transaction. */
public final class AtomicDownloadPublisher {
    /** Moves one file; the publisher uses it for publication and for restoring backups. */
    @FunctionalInterface
    public interface Mover {
        /** Moves {@code source} to {@code target}. */
        void move(@NotNull Path source, @NotNull Path target) throws IOException;
    }

    private static final class Backup {
        final Path originalTarget;
        final Path file;

        Backup(Path originalTarget, Path file) {
            this.originalTarget = originalTarget;
            this.file = file;
        }
    }

    private final Path libraryData;
    private final Mover mover;

    /** Publishes with {@link AtomicFiles#move(Path, Path)}. */
    public AtomicDownloadPublisher(@NotNull Path libraryData) {
        this(libraryData, new Mover() {
            @Override
            public void move(@NotNull Path source, @NotNull Path target) throws IOException {
                AtomicFiles.move(source, target);
            }
        });
    }

    /** Publishes with {@code mover}, so a test can fail a step. */
    public AtomicDownloadPublisher(@NotNull Path libraryData, @NotNull Mover mover) {
        this.libraryData = libraryData;
        this.mover = mover;
    }

    /** Moves every staged file to its target; on any failure restores the originals and rethrows. */
    public void publish(@NotNull List<PreparedDownload> downloads) throws IOException {
        Path transactionDirectory = libraryData.resolve("downloads/transactions/" + UUID.randomUUID());
        List<Backup> backups = new ArrayList<Backup>();
        List<Path> publishedTargets = new ArrayList<Path>();
        try {
            for (int index = 0; index < downloads.size(); index++) {
                PreparedDownload download = downloads.get(index);
                Files.createDirectories(download.getTarget().getParent());
                Backup backup = backupExistingTarget(download.getTarget(), index, transactionDirectory);
                if (backup != null) {
                    backups.add(backup);
                }
                mover.move(download.getStaging(), download.getTarget());
                publishedTargets.add(download.getTarget());
            }
        } catch (Throwable error) {
            rollback(error, publishedTargets, backups);
            // A backup that could not be restored is the only remaining copy of the original file.
            CleanupSteps.suppressInto(error,
                () -> cleanTemporaryFiles(downloads, Collections.<Backup>emptyList(), transactionDirectory));
            throw error;
        }
        cleanTemporaryFiles(downloads, backups, transactionDirectory);
    }

    private Backup backupExistingTarget(Path target, int index, Path transactionDirectory) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Download target must be a regular file: " + target);
        }
        Path backup = transactionDirectory.resolve(index + "-" + target.getFileName());
        Files.createDirectories(backup.getParent());
        Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING);
        return new Backup(target, backup);
    }

    private void rollback(Throwable primary, List<Path> publishedTargets, List<Backup> backups) {
        List<CleanupSteps.Step> steps = new ArrayList<CleanupSteps.Step>();
        for (final Path target : reversed(publishedTargets)) {
            steps.add(() -> Files.deleteIfExists(target));
        }
        for (final Backup backup : reversed(backups)) {
            steps.add(() -> {
                if (Files.exists(backup.file)) {
                    mover.move(backup.file, backup.originalTarget);
                }
            });
        }
        CleanupSteps.suppressInto(primary, steps.toArray(new CleanupSteps.Step[0]));
    }

    private void cleanTemporaryFiles(List<PreparedDownload> downloads, List<Backup> backups, Path transactionDirectory) {
        List<CleanupSteps.Step> steps = new ArrayList<CleanupSteps.Step>();
        for (final PreparedDownload download : downloads) {
            steps.add(() -> Files.deleteIfExists(download.getStaging()));
        }
        for (final Backup backup : backups) {
            steps.add(() -> Files.deleteIfExists(backup.file));
        }
        steps.add(() -> {
            try {
                Files.deleteIfExists(transactionDirectory);
            } catch (IOException ignored) {
                // The directory may still hold files of a failed restore; they must stay.
            }
        });
        CleanupSteps.closeAll(steps.toArray(new CleanupSteps.Step[0]));
    }

    private static <T> List<T> reversed(List<T> items) {
        List<T> copy = new ArrayList<T>(items);
        Collections.reverse(copy);
        return copy;
    }
}
