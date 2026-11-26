package org.pnfolder.pnlibrary.api.downloads.source;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.platform.PlatformType;

/** Verified HTTPS source and runtime compatibility constraints for one file. */
public final class DirectDownloadSource {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    private final URI uri;
    private final PlatformType platform;
    private final int minimumJava;
    private final Integer maximumJava;
    private final Long size;
    private final String sha256;
    private final Long maxBytes;
    private final List<URI> mirrors;

    private DirectDownloadSource(Builder builder) {
        if (builder.uri == null) {
            throw new IllegalArgumentException("Download URL is required");
        }
        this.uri = builder.uri;
        this.platform = builder.platform;
        this.minimumJava = builder.minimumJava;
        this.maximumJava = builder.maximumJava;
        this.size = builder.size;
        this.sha256 = builder.sha256;
        this.maxBytes = builder.maxBytes;
        this.mirrors = Collections.unmodifiableList(new ArrayList<>(builder.mirrors));
    }

    /** The HTTPS URI from which the file is downloaded. */
    public @NotNull URI getUri() {
        return uri;
    }

    /** The optional platform restriction. */
    public @Nullable PlatformType getPlatform() {
        return platform;
    }

    /** The oldest supported Java feature version. */
    public int getMinimumJava() {
        return minimumJava;
    }

    /** The newest supported Java feature version, or {@code null} when unbounded. */
    public @Nullable Integer getMaximumJava() {
        return maximumJava;
    }

    /** The expected byte length, when integrity metadata is available. */
    public @Nullable Long getSize() {
        return size;
    }

    /** The expected lowercase SHA-256 digest, when available. */
    public @Nullable String getSha256() {
        return sha256;
    }

    /** The largest accepted file in bytes for this file only; {@code null} means the library-wide limit (512 MB). */
    public @Nullable Long getMaxBytes() {
        return maxBytes;
    }

    /**
     * Other HTTPS addresses of the same file, tried in order after {@link #getUri()} when it does not answer; with a
     * checksum, the first file that matches wins.
     */
    public @NotNull List<URI> getMirrors() {
        return mirrors;
    }

    /** Returns whether this source supports {@code platform} on {@code javaFeature}. */
    public boolean supports(@NotNull PlatformType platform, int javaFeature) {
        return (this.platform == null || this.platform == platform) && javaFeature >= minimumJava
            && (maximumJava == null || javaFeature <= maximumJava);
    }

    /** Creates a new {@link Builder}. */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /** Fluent builder for a validated {@link DirectDownloadSource}. */
    public static final class Builder {
        private URI uri;
        private PlatformType platform;
        private int minimumJava = 8;
        private Integer maximumJava;
        private Long size;
        private String sha256;
        private Long maxBytes;
        private final List<URI> mirrors = new ArrayList<>();

        private Builder() {
        }

        /** Sets the HTTPS URL to download from. */
        public @NotNull Builder url(@NotNull String value) {
            uri = HttpsUris.require(value, "Download URL must use HTTPS");
            return this;
        }

        /** Adds another HTTPS address of the same file. */
        public @NotNull Builder mirror(@NotNull String value) {
            mirrors.add(HttpsUris.require(value, "Mirror URL must use HTTPS"));
            return this;
        }

        /** Restricts the source to {@code value}. */
        public @NotNull Builder platform(@NotNull PlatformType value) {
            platform = value;
            return this;
        }

        /** Restricts the source to Java feature versions from {@code minimum} on. */
        public @NotNull Builder java(int minimum) {
            return java(minimum, null);
        }

        /** Restricts the source to the inclusive Java feature-version range; a {@code null} maximum is open. */
        public @NotNull Builder java(int minimum, @Nullable Integer maximum) {
            if (minimum < 8 || (maximum != null && maximum < minimum)) {
                throw new IllegalArgumentException("Java version range is invalid");
            }
            minimumJava = minimum;
            maximumJava = maximum;
            return this;
        }

        /** Sets the expected size and SHA-256 checksum the downloaded file must match. */
        public @NotNull Builder integrity(long size, @NotNull String sha256) {
            if (size <= 0) {
                throw new IllegalArgumentException("Download size must be positive");
            }
            requireSha256(sha256);
            this.size = size;
            this.sha256 = sha256.toLowerCase(Locale.ROOT);
            return this;
        }

        /** Sets the largest accepted size of this file in bytes, smaller or larger than the library-wide limit (up to 2 GB). */
        public @NotNull Builder maxBytes(long value) {
            if (value < 1 || value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Size limit must be between 1 byte and 2 GB");
            }
            maxBytes = value;
            return this;
        }

        /** Sets the SHA-256 checksum the downloaded file must match; the size is not checked. */
        public @NotNull Builder sha256(@NotNull String sha256) {
            requireSha256(sha256);
            this.sha256 = sha256.toLowerCase(Locale.ROOT);
            return this;
        }

        /** Validates this configuration and creates an immutable source. */
        public @NotNull DirectDownloadSource build() {
            return new DirectDownloadSource(this);
        }

        private static void requireSha256(String value) {
            if (!SHA256.matcher(value).matches()) {
                throw new IllegalArgumentException("SHA-256 must contain 64 hexadecimal characters");
            }
        }
    }
}
