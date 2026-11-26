package org.pnfolder.pnlibrary.api.downloads;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.downloads.source.DirectDownloadSource;
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination;
import org.pnfolder.pnlibrary.api.platform.PlatformType;

/** Declarative description of one file managed by the download service. */
public final class FileDownload {
    private final String key;
    private final DirectDownloadSource source;
    private final boolean required;
    private final DownloadPolicy policy;
    private final DownloadDestination destination;
    private final String relativePath;

    private FileDownload(Builder builder) {
        this.key = builder.id.trim();
        this.source = builder.source.build();
        this.required = builder.required;
        this.policy = builder.policy;
        this.destination = builder.destination;
        this.relativePath = safeRelativePath(builder.relativePath);
    }

    /** The application-defined download identifier. */
    public @NotNull String getKey() {
        return key;
    }

    /** The verified remote source and compatibility constraints. */
    public @NotNull DirectDownloadSource getSource() {
        return source;
    }

    /** Whether failure to obtain this file blocks its owner. */
    public boolean getRequired() {
        return required;
    }

    /** Whether the file is downloaded by itself or only when an administrator asks. */
    public @NotNull DownloadPolicy getPolicy() {
        return policy;
    }

    /** The root used to resolve {@link #getRelativePath()}. */
    public @NotNull DownloadDestination getDestination() {
        return destination;
    }

    /** The normalized path confined to the selected destination. */
    public @NotNull String getRelativePath() {
        return relativePath;
    }

    private static String safeRelativePath(String value) {
        Path path = Paths.get(value).normalize();
        if (path.isAbsolute() || path.getNameCount() == 0 || path.startsWith("..")) {
            throw new IllegalArgumentException("Download destination must stay inside its root");
        }
        return path.toString().replace('\\', '/');
    }

    /** Fluent builder for a validated {@link FileDownload}. */
    public static final class Builder {
        private final String id;
        private final DirectDownloadSource.Builder source = DirectDownloadSource.builder();
        private boolean required = true;
        private DownloadPolicy policy = DownloadPolicy.MANUAL;
        private DownloadDestination destination = DownloadDestination.DATA_FOLDER;
        private String relativePath = "";

        Builder(String id) {
            this.id = id;
        }

        /** Lets the owner work without this file: a failed download does not stop the whole batch. */
        public @NotNull Builder optional() {
            required = false;
            return this;
        }

        /**
         * Whether the file is downloaded by itself ({@link DownloadPolicy#AUTOMATIC}, {@link DownloadPolicy#FORCED})
         * or only when asked; {@link DownloadPolicy#MANUAL} by default.
         */
        public @NotNull Builder policy(@NotNull DownloadPolicy value) {
            policy = value;
            return this;
        }

        /** Sets the HTTPS URL to download from. */
        public @NotNull Builder url(@NotNull String value) {
            source.url(value);
            return this;
        }

        /** Adds another HTTPS address of the same file; tried in order when the one before does not answer. */
        public @NotNull Builder mirror(@NotNull String value) {
            source.mirror(value);
            return this;
        }

        /** Restricts the source to {@code value}. */
        public @NotNull Builder platform(@NotNull PlatformType value) {
            source.platform(value);
            return this;
        }

        /** Restricts the source to Java feature versions from {@code minimum} on. */
        public @NotNull Builder java(int minimum) {
            source.java(minimum);
            return this;
        }

        /** Restricts the source to the inclusive Java feature-version range; a {@code null} maximum is open. */
        public @NotNull Builder java(int minimum, @Nullable Integer maximum) {
            source.java(minimum, maximum);
            return this;
        }

        /** Sets the expected size and SHA-256 checksum the downloaded file must match. */
        public @NotNull Builder integrity(long size, @NotNull String sha256) {
            source.integrity(size, sha256);
            return this;
        }

        /** Sets the largest accepted size of this file in bytes; without it the library-wide limit applies. */
        public @NotNull Builder maxBytes(long value) {
            source.maxBytes(value);
            return this;
        }

        /** Sets the SHA-256 checksum the downloaded file must match. */
        public @NotNull Builder sha256(@NotNull String value) {
            source.sha256(value);
            return this;
        }

        /** Selects the destination root and confined relative path. */
        public @NotNull Builder destination(@NotNull DownloadDestination value, @NotNull String relativePath) {
            this.destination = value;
            this.relativePath = relativePath;
            return this;
        }

        FileDownload build() {
            if (id.trim().isEmpty()) {
                throw new IllegalArgumentException("File download ID is required");
            }
            if (relativePath.trim().isEmpty()) {
                throw new IllegalArgumentException("Download destination path is required");
            }
            return new FileDownload(this);
        }
    }
}
