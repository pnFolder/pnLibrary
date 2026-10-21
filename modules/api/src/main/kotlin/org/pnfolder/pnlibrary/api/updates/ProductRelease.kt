package org.pnfolder.pnlibrary.api.updates

import org.pnfolder.pnlibrary.api.version.ApiVersionRange
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import java.time.Instant

/**
 * Normalized release metadata available from a release source.
 *
 * @property product released product identity
 * @property version released semantic version
 * @property channel declared release maturity
 * @property supportedApi pnLibrary API generations supported by this release
 * @property providesApi API generation supplied by this product, when applicable
 * @property dependencies managed product requirements
 * @property repository source repository in `owner/name` form
 * @property artifacts downloadable platform artifacts
 * @property externalPluginDependencies third-party plugin requirements
 * @property publishedAt publisher-provided release time
 */
data class ProductRelease(
    val product: ProductId,
    val version: SemanticVersion,
    val channel: UpdateChannel,
    val supportedApi: ApiVersionRange,
    val providesApi: Int? = null,
    val dependencies: List<ProductDependency> = emptyList(),
    val repository: String? = null,
    val artifacts: List<ArtifactDescriptor> = emptyList(),
    val externalPluginDependencies: List<ExternalPluginDependency> = emptyList(),
    val publishedAt: Instant? = null,
    /** Link to the human-readable changelog for this release, when published. */
    val changelogUrl: String? = null,
    /** Short release summary suitable for update notifications. */
    val description: String = "",
) {
    init {
        requireProvidedApi(providesApi)
        require(dependencies.map(ProductDependency::product).distinct().size == dependencies.size) {
            "Product release contains duplicate dependencies"
        }
    }
}
