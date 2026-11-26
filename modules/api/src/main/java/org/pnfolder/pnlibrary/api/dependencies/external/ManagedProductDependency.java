package org.pnfolder.pnlibrary.api.dependencies.external;

import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.dependencies.VersionConstraint;
import org.pnfolder.pnlibrary.api.downloads.DownloadPolicy;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

/** A managed component dependency discoverable through a release catalogue. */
public final class ManagedProductDependency implements PluginDependency {
    private static final Pattern REPOSITORY_PART = Pattern.compile("[A-Za-z0-9_.-]+");

    private final ProductId product;
    private final VersionConstraint versions;
    private final String repositoryOwner;
    private final String repositoryName;
    private final boolean required;
    private final DownloadPolicy downloadPolicy;

    /**
     * Creates the dependency.
     *
     * @param product required managed product
     * @param versions accepted semantic-version interval
     * @param repositoryOwner catalogue repository owner
     * @param repositoryName catalogue repository name
     * @param required whether absence blocks the declaring product
     * @param downloadPolicy administrator/automatic download policy
     */
    public ManagedProductDependency(@NotNull ProductId product, @NotNull VersionConstraint versions,
                                    @NotNull String repositoryOwner, @NotNull String repositoryName,
                                    boolean required, @NotNull DownloadPolicy downloadPolicy) {
        if (!REPOSITORY_PART.matcher(repositoryOwner).matches()) {
            throw new IllegalArgumentException("Invalid repository owner: " + repositoryOwner);
        }
        if (!REPOSITORY_PART.matcher(repositoryName).matches()) {
            throw new IllegalArgumentException("Invalid repository name: " + repositoryName);
        }
        this.product = product;
        this.versions = versions;
        this.repositoryOwner = repositoryOwner;
        this.repositoryName = repositoryName;
        this.required = required;
        this.downloadPolicy = downloadPolicy;
    }

    /** Creates a dependency accepting every version from {@code minimumVersion} up. */
    public ManagedProductDependency(@NotNull ProductId product, @NotNull SemanticVersion minimumVersion,
                                    @NotNull String repositoryOwner, @NotNull String repositoryName,
                                    boolean required, @NotNull DownloadPolicy downloadPolicy) {
        this(product, new VersionConstraint(minimumVersion), repositoryOwner, repositoryName, required,
            downloadPolicy);
    }

    /** Creates a dependency on the product named {@code component}, accepting every version from {@code minimumVersion}. */
    public ManagedProductDependency(@NotNull String component, @NotNull String minimumVersion,
                                    @NotNull String repositoryOwner, @NotNull String repositoryName,
                                    boolean required, @NotNull DownloadPolicy downloadPolicy) {
        this(ProductId.of(component), new VersionConstraint(SemanticVersion.parse(minimumVersion)), repositoryOwner,
            repositoryName, required, downloadPolicy);
    }

    /** Required managed product. */
    @NotNull
    public ProductId getProduct() {
        return product;
    }

    /** Accepted semantic-version interval. */
    @NotNull
    @Override
    public VersionConstraint getVersions() {
        return versions;
    }

    /** Catalogue repository owner. */
    @NotNull
    public String getRepositoryOwner() {
        return repositoryOwner;
    }

    /** Catalogue repository name. */
    @NotNull
    public String getRepositoryName() {
        return repositoryName;
    }

    /** Whether absence blocks the declaring product. */
    @Override
    public boolean getRequired() {
        return required;
    }

    /** Administrator/automatic download policy. */
    @NotNull
    @Override
    public DownloadPolicy getDownloadPolicy() {
        return downloadPolicy;
    }

    /** Oldest accepted managed product version. */
    @NotNull
    public SemanticVersion getMinimumVersion() {
        return versions.getMinimum();
    }

    /** Returns this dependency as its managed representation. */
    @NotNull
    @Override
    public ManagedProductDependency getManaged() {
        return this;
    }
}
