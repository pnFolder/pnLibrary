package org.pnfolder.pnlibrary.updates.history;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.io.Digests;

/** Copies and checks the saved bytes of configuration snapshots within a size limit. */
final class SnapshotBlobs {
    private static final int BUFFER_SIZE = 8192;

    private SnapshotBlobs() {
    }

    /** The size and SHA-256 of a copied file. */
    static final class Copied {
        private final long size;
        private final String sha256;

        Copied(long size, String sha256) {
            this.size = size;
            this.sha256 = sha256;
        }

        long getSize() {
            return size;
        }

        String getSha256() {
            return sha256;
        }
    }

    /** Copies {@code source} to a new file {@code target}; fails once more than {@code maximum} bytes were read. */
    @NotNull
    static Copied copyNew(@NotNull Path source, @NotNull Path target, long maximum) throws IOException {
        return copy(source, target, maximum,
            new OpenOption[] {StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE});
    }

    /** Copies {@code source} over the existing file {@code target}, which is never a link. */
    @NotNull
    static Copied copyOver(@NotNull Path source, @NotNull Path target, long maximum) throws IOException {
        return copy(source, target, maximum, new OpenOption[] {StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS});
    }

    private static Copied copy(Path source, Path target, long maximum, OpenOption[] options) throws IOException {
        MessageDigest digest = newDigest();
        long count = 0;
        try (InputStream input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS);
             OutputStream output = Files.newOutputStream(target, options)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                count += read;
                if (count > maximum) {
                    throw new IllegalArgumentException("Configuration snapshot exceeds size limit");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        return new Copied(count, Digests.hex(digest.digest()));
    }

    /** The SHA-256 of {@code path}; fails when the file is larger than {@code limit}. */
    @NotNull
    static String hash(@NotNull Path path, long limit) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0;
            for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                total += read;
                if (total > limit) {
                    throw new IllegalArgumentException("Configuration backup exceeds size limit");
                }
                digest.update(buffer, 0, read);
            }
        }
        return Digests.hex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available", error);
        }
    }
}
