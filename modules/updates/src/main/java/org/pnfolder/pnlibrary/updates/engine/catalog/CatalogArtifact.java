package org.pnfolder.pnlibrary.updates.engine.catalog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.platform.PlatformType;

import java.net.URI;
import java.util.Objects;

/** One platform artifact declared by a catalog release. */
public final class CatalogArtifact {
    private final String file;
    private final PlatformType platform;
    private final VersionRangeText minecraft;
    private final VersionRangeText platformApi;
    private final int javaMinimum;
    private final Integer javaMaximum;
    private final URI url;
    private final String sha256;
    private final Long size;

    /**
     * @param file safe JAR filename
     * @param platform required platform family, or {@link PlatformType#UNIVERSAL} for one JAR built for every platform
     * @param minecraft optional compatible Minecraft range
     * @param platformApi optional compatible proxy API range
     * @param javaMinimum oldest supported Java feature version
     * @param javaMaximum newest supported Java feature version
     * @param url HTTPS artifact location
     * @param sha256 optional published SHA-256 digest
     * @param size optional published byte size
     */
    public CatalogArtifact(@NotNull String file, @NotNull PlatformType platform, @Nullable VersionRangeText minecraft,
                           @Nullable VersionRangeText platformApi, int javaMinimum, @Nullable Integer javaMaximum,
                           @NotNull URI url, @Nullable String sha256, @Nullable Long size) {
        this.file = file;
        this.platform = platform;
        this.minecraft = minecraft;
        this.platformApi = platformApi;
        this.javaMinimum = javaMinimum;
        this.javaMaximum = javaMaximum;
        this.url = url;
        this.sha256 = sha256;
        this.size = size;
    }

    @NotNull
    public String getFile() {
        return file;
    }

    @NotNull
    public PlatformType getPlatform() {
        return platform;
    }

    @Nullable
    public VersionRangeText getMinecraft() {
        return minecraft;
    }

    @Nullable
    public VersionRangeText getPlatformApi() {
        return platformApi;
    }

    public int getJavaMinimum() {
        return javaMinimum;
    }

    @Nullable
    public Integer getJavaMaximum() {
        return javaMaximum;
    }

    @NotNull
    public URI getUrl() {
        return url;
    }

    @Nullable
    public String getSha256() {
        return sha256;
    }

    @Nullable
    public Long getSize() {
        return size;
    }

    /** Whether this artifact runs on {@code target}: an exact platform match, or one built for every platform. */
    public boolean matchesPlatform(@NotNull PlatformType target) {
        return platform == target || platform == PlatformType.UNIVERSAL;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CatalogArtifact)) {
            return false;
        }
        CatalogArtifact that = (CatalogArtifact) other;
        return file.equals(that.file) && platform == that.platform && Objects.equals(minecraft, that.minecraft)
            && Objects.equals(platformApi, that.platformApi) && javaMinimum == that.javaMinimum
            && Objects.equals(javaMaximum, that.javaMaximum) && url.equals(that.url)
            && Objects.equals(sha256, that.sha256) && Objects.equals(size, that.size);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, platform, minecraft, platformApi, javaMinimum, javaMaximum, url, sha256, size);
    }

    @Override
    public String toString() {
        return "CatalogArtifact(file=" + file + ", platform=" + platform + ", minecraft=" + minecraft
            + ", platformApi=" + platformApi + ", javaMinimum=" + javaMinimum + ", javaMaximum=" + javaMaximum
            + ", url=" + url + ", sha256=" + sha256 + ", size=" + size + ")";
    }
}
