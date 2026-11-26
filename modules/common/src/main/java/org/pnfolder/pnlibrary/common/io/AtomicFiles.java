package org.pnfolder.pnlibrary.common.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

/**
 * File replacement that readers never observe half-written.
 *
 * <p>Content is written to a temporary file beside the target and then moved over it. File systems that cannot move
 * atomically get an ordinary move instead, which is still a single rename on every local file system.
 */
public final class AtomicFiles {
    private AtomicFiles() {
    }

    /** Moves {@code source} onto {@code target}, replacing an existing target. */
    public static void move(@NotNull Path source, @NotNull Path target) throws IOException {
        move(source, target, true);
    }

    /**
     * Moves {@code source} onto {@code target}, atomically when the file system supports it.
     *
     * @param replaceExisting whether an existing target is replaced; otherwise the move fails if it exists
     * @throws FileAlreadyExistsException when the target exists and {@code replaceExisting} is false; checked up front
     *     because an atomic move may replace the target regardless of options on some platforms, Windows among them
     */
    public static void move(@NotNull Path source, @NotNull Path target, boolean replaceExisting) throws IOException {
        if (!replaceExisting && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(target.toString());
        }
        CopyOption[] options = replaceExisting
            ? new CopyOption[] {StandardCopyOption.REPLACE_EXISTING}
            : new CopyOption[0];
        try {
            Files.move(source, target, withAtomicMove(options));
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, options);
        }
    }

    /** Replaces {@code target} with {@code bytes}, creating parent directories as needed. */
    public static void write(@NotNull Path target, @NotNull byte[] bytes) throws IOException {
        write(target, bytes, false);
    }

    /**
     * Replaces {@code target} with {@code bytes}, creating parent directories as needed.
     *
     * @param durable whether the content is flushed to the storage device before it replaces the target, for state
     *     that must survive a power loss
     */
    public static void write(@NotNull Path target, @NotNull byte[] bytes, boolean durable) throws IOException {
        replace(target, durable, temporary -> Files.write(temporary, bytes));
    }

    /**
     * Replaces {@code target} with what {@code fill} writes to the temporary file it is given. The temporary file is
     * removed whether or not {@code fill} succeeds.
     */
    public static void write(@NotNull Path target, @NotNull Consumer<Path> fill) throws IOException {
        write(target, false, fill);
    }

    /**
     * Replaces {@code target} with what {@code fill} writes to the temporary file it is given.
     *
     * @param durable whether the content is flushed to the storage device before it replaces the target
     */
    public static void write(@NotNull Path target, boolean durable, @NotNull Consumer<Path> fill) throws IOException {
        replace(target, durable, fill::accept);
    }

    private static void replace(Path target, boolean durable, Filler filler) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            filler.fill(temporary);
            if (durable) {
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
            }
            move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static CopyOption[] withAtomicMove(CopyOption[] options) {
        CopyOption[] result = new CopyOption[options.length + 1];
        result[0] = StandardCopyOption.ATOMIC_MOVE;
        System.arraycopy(options, 0, result, 1, options.length);
        return result;
    }

    // Internal: lets the byte and file writers throw IOException without wrapping it for the public Consumer.
    private interface Filler {
        void fill(Path temporary) throws IOException;
    }
}
