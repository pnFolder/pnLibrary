package ru.privatenull.pnlibrary.api.updates

import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.api.plugin.DownloadPolicy
import ru.privatenull.pnlibrary.api.plugin.VersionConstraint

/**
 * A managed component dependency discoverable through a release catalogue.
 *
 * @property product required managed product
 * @property versions accepted semantic-version interval
 * @property repositoryOwner catalogue repository owner
 * @property repositoryName catalogue repository name
 * @property required whether absence blocks the declaring product
 * @property downloadPolicy administrator/automatic download policy
 */
class ManagedProductDependency(
    val product: ProductId,
    override val versions: VersionConstraint,
    val repositoryOwner: String,
    val repositoryName: String,
    override val required: Boolean = true,
    override val downloadPolicy: DownloadPolicy = DownloadPolicy.MANUAL,
) : PluginDependency {
    /** Oldest accepted managed product version. */
    val minimumVersion: SemanticVersion get() = versions.minimum

    constructor(product: ProductId, minimumVersion: SemanticVersion, repositoryOwner: String, repositoryName: String,
                required: Boolean = true, downloadPolicy: DownloadPolicy = DownloadPolicy.MANUAL) : this(
        product, VersionConstraint(minimumVersion), repositoryOwner, repositoryName, required, downloadPolicy,
    )

    constructor(component: String, minimumVersion: String, repositoryOwner: String, repositoryName: String,
                required: Boolean = true, downloadPolicy: DownloadPolicy = DownloadPolicy.MANUAL) : this(
        ProductId.of(component), VersionConstraint(SemanticVersion.parse(minimumVersion)), repositoryOwner, repositoryName,
        required, downloadPolicy,
    )
    /** Returns this dependency as its managed representation. */
    override val managed: ManagedProductDependency get() = this
    init {
        require(REPOSITORY_PART.matches(repositoryOwner)) { "invalid repository owner: $repositoryOwner" }
        require(REPOSITORY_PART.matches(repositoryName)) { "invalid repository name: $repositoryName" }
    }

    /** Validation constants for repository coordinates. */
    companion object {
        private val REPOSITORY_PART = Regex("[A-Za-z0-9_.-]+")
    }
}
