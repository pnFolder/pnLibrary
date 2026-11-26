package org.pnfolder.pnlibrary.core.diagnostics.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads a bounded regular file confined to a trusted configuration directory.
 *
 * <p>Validation and reading are intentionally centralized so exact and redacted diagnostics follow the same path
 * policy. Symbolic links, traversal outside the root, non-regular files, known binary formats, and files larger
 * than the byte budget are rejected before their contents enter the report pipeline.
 */
public final class SecureConfigReader {
    private static final long DEFAULT_MAXIMUM_BYTES = 1_048_576L;
    private static final Set<String> FORBIDDEN_EXTENSIONS = new HashSet<String>(Arrays.asList(
        ".db", ".sqlite", ".sqlite3", ".db-shm", ".db-wal", ".bin", ".dat",
        ".class", ".jar", ".zip", ".tar", ".gz", ".png", ".jpg", ".jpeg", ".ico"));

    private final long maximumBytes;

    /** Creates a reader that accepts files up to 1 MiB. */
    public SecureConfigReader() {
        this(DEFAULT_MAXIMUM_BYTES);
    }

    /** Creates a reader that accepts files up to {@code maximumBytes} bytes. */
    public SecureConfigReader(long maximumBytes) {
        this.maximumBytes = maximumBytes;
    }

    /**
     * Resolves {@code relativePath} beneath {@code rootDirectory} and reads its bytes.
     *
     * <p>Operational failures are represented by {@link Result#getError()}, allowing report generation to continue
     * with an explanatory entry.
     */
    @NotNull
    public Result read(@NotNull Path rootDirectory, @NotNull String relativePath) {
        Path root = rootDirectory.toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        String rejected = rejection(root, target, relativePath);
        if (rejected != null) {
            return Result.failure(rejected);
        }
        Path realRoot = realPath(root);
        if (realRoot == null) {
            return Result.failure("[SECURITY: configuration root cannot be resolved]");
        }
        Path realTarget = realPath(target);
        if (realTarget == null) {
            return Result.failure("[SECURITY: configuration path cannot be resolved]");
        }
        if (!realTarget.startsWith(realRoot)) {
            return Result.failure("[SECURITY: symlink escape blocked]");
        }
        return readBounded(realTarget);
    }

    private String rejection(Path root, Path target, String relativePath) {
        if (!target.startsWith(root)) {
            return "[SECURITY: path traversal blocked]";
        }
        if (Files.isSymbolicLink(target)) {
            return "[SECURITY: symlink escape blocked]";
        }
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return "[file not found or not a regular file]";
        }
        if (hasForbiddenExtension(relativePath)) {
            return "[SECURITY: binary or database file extension blocked]";
        }
        return null;
    }

    private Result readBounded(Path file) {
        try {
            long size = Files.size(file);
            if (size > maximumBytes) {
                return Result.failure("[file exceeds size limit of " + maximumBytes + " bytes (" + size + " bytes)]");
            }
            return Result.success(Files.readAllBytes(file));
        } catch (IOException | RuntimeException exception) {
            return Result.failure("[read failed: " + exception.getClass().getSimpleName() + "]");
        }
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException | RuntimeException unresolved) {
            // Reported by the caller as a security refusal, which is the safe reading of an unresolvable path.
            return null;
        }
    }

    private static boolean hasForbiddenExtension(String path) {
        String lowercase = path.toLowerCase(Locale.ROOT);
        for (String extension : FORBIDDEN_EXTENSIONS) {
            if (lowercase.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    /** Result of one confined file read. Exactly one of the content and the error is set. */
    public static final class Result {
        private final byte[] content;
        private final String error;

        private Result(byte[] content, String error) {
            this.content = content;
            this.error = error;
        }

        static Result success(byte[] content) {
            return new Result(content, null);
        }

        static Result failure(String error) {
            return new Result(null, error);
        }

        /** The bytes read, or {@code null} when the read failed. */
        @Nullable
        public byte[] getContent() {
            return content;
        }

        /** Why the read failed, or {@code null} when it succeeded. */
        @Nullable
        public String getError() {
            return error;
        }
    }
}
