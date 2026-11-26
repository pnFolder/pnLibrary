package org.pnfolder.pnlibrary.updates.engine.catalog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One catalog release. */
public final class CatalogRelease {
    private final SemanticVersion version;
    private final UpdateChannel channel;
    private final String description;
    private final URI changelogUrl;
    private final Instant publishedAt;
    private final ApiVersionRange api;
    private final List<CatalogArtifact> artifacts;

    public CatalogRelease(@NotNull SemanticVersion version, @NotNull UpdateChannel channel, @NotNull String description,
                          @NotNull Instant publishedAt, @NotNull ApiVersionRange api, @NotNull List<CatalogArtifact> artifacts) {
        this(version, channel, description, null, publishedAt, api, artifacts);
    }

    /**
     * @param version semantic release version
     * @param channel declared release maturity
     * @param description human-readable summary
     * @param changelogUrl link to the list of changes, when published
     * @param publishedAt UTC publication instant
     * @param api supported pnLibrary API generations
     * @param artifacts platform-specific downloadable artifacts
     */
    public CatalogRelease(@NotNull SemanticVersion version, @NotNull UpdateChannel channel, @NotNull String description,
                          @Nullable URI changelogUrl, @NotNull Instant publishedAt, @NotNull ApiVersionRange api,
                          @NotNull List<CatalogArtifact> artifacts) {
        this.version = Objects.requireNonNull(version, "version");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.description = Objects.requireNonNull(description, "description");
        this.changelogUrl = changelogUrl;
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
        this.api = Objects.requireNonNull(api, "api");
        this.artifacts = Collections.unmodifiableList(new ArrayList<>(artifacts));
    }

    @NotNull
    public SemanticVersion getVersion() {
        return version;
    }

    @NotNull
    public UpdateChannel getChannel() {
        return channel;
    }

    @NotNull
    public String getDescription() {
        return description;
    }

    @Nullable
    public URI getChangelogUrl() {
        return changelogUrl;
    }

    @NotNull
    public Instant getPublishedAt() {
        return publishedAt;
    }

    @NotNull
    public ApiVersionRange getApi() {
        return api;
    }

    @NotNull
    public List<CatalogArtifact> getArtifacts() {
        return artifacts;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CatalogRelease)) {
            return false;
        }
        CatalogRelease that = (CatalogRelease) other;
        return version.equals(that.version) && channel == that.channel && description.equals(that.description)
            && Objects.equals(changelogUrl, that.changelogUrl) && publishedAt.equals(that.publishedAt)
            && api.equals(that.api) && artifacts.equals(that.artifacts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, channel, description, changelogUrl, publishedAt, api, artifacts);
    }

    @Override
    public String toString() {
        return "CatalogRelease(version=" + version + ", channel=" + channel + ", description=" + description
            + ", changelogUrl=" + changelogUrl + ", publishedAt=" + publishedAt + ", api=" + api
            + ", artifacts=" + artifacts + ")";
    }
}
