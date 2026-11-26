package org.pnfolder.pnlibrary.updates.history;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Disk space of saved rollback sets and their removal. */
public final class BackupStorage {
    /** Rollback sets older than this are removed on their own. */
    public static final Duration RETENTION = Duration.ofDays(30);

    /** A set this large is reported to the administrator, who can delete it. */
    public static final long LARGE_BYTES = 500L * 1024 * 1024;

    private BackupStorage() {
    }

    /** Total size of the files under {@code folder}; 0 when it does not exist. */
    public static long bytes(@NotNull Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return 0;
        }
        long total = 0;
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (Files.isRegularFile(path)) {
                    total += Files.size(path);
                }
            }
        }
        return total;
    }

    /** True when a set created at {@code createdAt} has outlived {@link #RETENTION}; a set with no date is kept. */
    public static boolean expired(@Nullable Instant createdAt, @NotNull Instant now) {
        return createdAt != null && createdAt.plus(RETENTION).isBefore(now);
    }

    /** Removes {@code folder} with everything in it; does nothing when it does not exist. */
    public static void delete(@NotNull Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(path);
            }
        }
    }

    /** The folder of {@code name} inside {@code parent}, whatever its letter case, or null when there is none. */
    @Nullable
    public static Path child(@NotNull Path parent, @NotNull String name) throws IOException {
        if (!Files.isDirectory(parent)) {
            return null;
        }
        try (Stream<Path> paths = Files.list(parent)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (Files.isDirectory(path) && path.getFileName().toString().equalsIgnoreCase(name)) {
                    return path;
                }
            }
        }
        return null;
    }

    /** Copies every file under {@code from} into {@code to}, keeping the folder structure. */
    public static void copy(@NotNull Path from, @NotNull Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path target = to.resolve(from.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    PrivateFiles.directories(target);
                } else {
                    PrivateFiles.directories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
