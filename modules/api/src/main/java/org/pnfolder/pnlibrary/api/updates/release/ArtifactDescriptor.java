package org.pnfolder.pnlibrary.api.updates.release;

import java.net.URI;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.UpdateValidation;

/** One exact platform/Java release artifact. */
public final class ArtifactDescriptor {
    private static final Pattern SAFE_FILE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final String file;
    private final PlatformType platform;
    private final int minimumJava;
    private final Integer maximumJava;
    private final Long size;
    private final String sha256;
    private final URI downloadUri;

    /**
     * Creates a descriptor. The publisher metadata {@code size} and {@code sha256} are optional; the updater computes
     * them after download.
     *
     * @param file safe JAR filename
     * @param platform required server/proxy platform
     * @param minimumJava oldest supported Java feature version
     * @param maximumJava newest supported Java feature version, or {@code null} when unbounded
     * @param size optional publisher-provided byte length
     * @param sha256 optional publisher-provided SHA-256 digest
     * @param downloadUri optional HTTPS artifact location
     */
    public ArtifactDescriptor(@NotNull String file, @NotNull PlatformType platform, int minimumJava,
                              @Nullable Integer maximumJava, @Nullable Long size, @Nullable String sha256,
                              @Nullable URI downloadUri) {
        if (!SAFE_FILE.matcher(file).matches() || !file.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
            throw new IllegalArgumentException("Unsafe artifact filename: " + file);
        }
        if (minimumJava < 8) {
            throw new IllegalArgumentException("Minimum Java must be at least 8");
        }
        if (maximumJava != null && maximumJava < minimumJava) {
            throw new IllegalArgumentException("Maximum Java must be >= minimum Java");
        }
        UpdateValidation.requirePositiveSize(size, "Artifact");
        UpdateValidation.requireSha256(sha256, "Artifact");
        if (downloadUri != null && !UpdateValidation.isHttps(downloadUri)) {
            throw new IllegalArgumentException("Artifact URL must use HTTPS");
        }
        this.file = file;
        this.platform = platform;
        this.minimumJava = minimumJava;
        this.maximumJava = maximumJava;
        this.size = size;
        this.sha256 = sha256;
        this.downloadUri = downloadUri;
    }

    /** Safe JAR filename. */
    @NotNull
    public String getFile() {
        return file;
    }

    /** Required server/proxy platform. */
    @NotNull
    public PlatformType getPlatform() {
        return platform;
    }

    /** Oldest supported Java feature version. */
    public int getMinimumJava() {
        return minimumJava;
    }

    /** Newest supported Java feature version, or {@code null} when unbounded. */
    @Nullable
    public Integer getMaximumJava() {
        return maximumJava;
    }

    /** Publisher-provided byte length, when known. */
    @Nullable
    public Long getSize() {
        return size;
    }

    /** Publisher-provided SHA-256 digest, when known. */
    @Nullable
    public String getSha256() {
        return sha256;
    }

    /** HTTPS artifact location, when known. */
    @Nullable
    public URI getDownloadUri() {
        return downloadUri;
    }

    /** Returns whether {@code javaFeature} is inside this artifact's Java range. */
    public boolean supports(int javaFeature) {
        return UpdateValidation.javaInRange(javaFeature, minimumJava, maximumJava);
    }

    /** Returns whether this artifact runs on {@code target}: an exact platform match, or one built for every platform. */
    public boolean matchesPlatform(@NotNull PlatformType target) {
        return platform == target || platform == PlatformType.UNIVERSAL;
    }
}
