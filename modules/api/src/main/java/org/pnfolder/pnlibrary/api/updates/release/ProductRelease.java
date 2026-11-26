package org.pnfolder.pnlibrary.api.updates.release;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.updates.UpdateValidation;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

/** Normalized release metadata available from a release source. */
public final class ProductRelease {
    private final ProductId product;
    private final SemanticVersion version;
    private final UpdateChannel channel;
    private final ApiVersionRange supportedApi;
    private final Integer providesApi;
    private final List<ProductDependency> dependencies;
    private final String repository;
    private final List<ArtifactDescriptor> artifacts;
    private final List<ExternalPluginDependency> externalPluginDependencies;
    private final Instant publishedAt;
    private final String changelogUrl;
    private final String description;

    /**
     * Creates the release.
     *
     * @param product released product identity
     * @param version released semantic version
     * @param channel declared release maturity
     * @param supportedApi pnLibrary API generations supported by this release
     * @param providesApi API generation supplied by this product, when applicable
     * @param dependencies managed product requirements
     * @param repository source repository in {@code owner/name} form
     * @param artifacts downloadable platform artifacts
     * @param externalPluginDependencies third-party plugin requirements
     * @param publishedAt publisher-provided release time
     * @param changelogUrl link to the human-readable changelog for this release, when published
     * @param description short release summary suitable for update notifications
     */
    public ProductRelease(@NotNull ProductId product, @NotNull SemanticVersion version,
                          @NotNull UpdateChannel channel, @NotNull ApiVersionRange supportedApi,
                          @Nullable Integer providesApi, @NotNull List<ProductDependency> dependencies,
                          @Nullable String repository, @NotNull List<ArtifactDescriptor> artifacts,
                          @NotNull List<ExternalPluginDependency> externalPluginDependencies,
                          @Nullable Instant publishedAt, @Nullable String changelogUrl,
                          @NotNull String description) {
        UpdateValidation.requireProvidedApi(providesApi);
        Set<ProductId> seen = new HashSet<>();
        for (ProductDependency dependency : dependencies) {
            if (!seen.add(dependency.getProduct())) {
                throw new IllegalArgumentException("Product release contains duplicate dependencies");
            }
        }
        this.product = product;
        this.version = version;
        this.channel = channel;
        this.supportedApi = supportedApi;
        this.providesApi = providesApi;
        this.dependencies = dependencies;
        this.repository = repository;
        this.artifacts = artifacts;
        this.externalPluginDependencies = externalPluginDependencies;
        this.publishedAt = publishedAt;
        this.changelogUrl = changelogUrl;
        this.description = description;
    }

    /** Creates a release with no dependencies, artifacts, repository or description. */
    public ProductRelease(@NotNull ProductId product, @NotNull SemanticVersion version,
                          @NotNull UpdateChannel channel, @NotNull ApiVersionRange supportedApi) {
        this(product, version, channel, supportedApi, null, Collections.emptyList(), null,
            Collections.emptyList(), Collections.emptyList(),
            null, null, "");
    }

    /** Released product identity. */
    @NotNull
    public ProductId getProduct() {
        return product;
    }

    /** Released semantic version. */
    @NotNull
    public SemanticVersion getVersion() {
        return version;
    }

    /** Declared release maturity. */
    @NotNull
    public UpdateChannel getChannel() {
        return channel;
    }

    /** pnLibrary API generations supported by this release. */
    @NotNull
    public ApiVersionRange getSupportedApi() {
        return supportedApi;
    }

    /** API generation supplied by this product, when applicable. */
    @Nullable
    public Integer getProvidesApi() {
        return providesApi;
    }

    /** Managed product requirements. */
    @NotNull
    public List<ProductDependency> getDependencies() {
        return dependencies;
    }

    /** Source repository in {@code owner/name} form. */
    @Nullable
    public String getRepository() {
        return repository;
    }

    /** Downloadable platform artifacts. */
    @NotNull
    public List<ArtifactDescriptor> getArtifacts() {
        return artifacts;
    }

    /** Third-party plugin requirements. */
    @NotNull
    public List<ExternalPluginDependency> getExternalPluginDependencies() {
        return externalPluginDependencies;
    }

    /** Publisher-provided release time. */
    @Nullable
    public Instant getPublishedAt() {
        return publishedAt;
    }

    /** Link to the human-readable changelog for this release, when published. */
    @Nullable
    public String getChangelogUrl() {
        return changelogUrl;
    }

    /** Short release summary suitable for update notifications. */
    @NotNull
    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ProductRelease)) {
            return false;
        }
        ProductRelease that = (ProductRelease) other;
        return product.equals(that.product) && version.equals(that.version) && channel == that.channel
            && supportedApi.equals(that.supportedApi) && Objects.equals(providesApi, that.providesApi)
            && dependencies.equals(that.dependencies) && Objects.equals(repository, that.repository)
            && artifacts.equals(that.artifacts) && externalPluginDependencies.equals(that.externalPluginDependencies)
            && Objects.equals(publishedAt, that.publishedAt) && Objects.equals(changelogUrl, that.changelogUrl)
            && description.equals(that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(product, version, channel, supportedApi, providesApi, dependencies, repository, artifacts,
            externalPluginDependencies, publishedAt, changelogUrl, description);
    }

    @Override
    public String toString() {
        return "ProductRelease(product=" + product + ", version=" + version + ", channel=" + channel
            + ", supportedApi=" + supportedApi + ", providesApi=" + providesApi + ", dependencies=" + dependencies
            + ", repository=" + repository + ", artifacts=" + artifacts
            + ", externalPluginDependencies=" + externalPluginDependencies + ", publishedAt=" + publishedAt
            + ", changelogUrl=" + changelogUrl + ", description=" + description + ")";
    }
}
