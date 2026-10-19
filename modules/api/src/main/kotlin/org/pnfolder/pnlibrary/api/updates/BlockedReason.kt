package org.pnfolder.pnlibrary.api.updates

import org.pnfolder.pnlibrary.api.version.ApiVersionRange
import org.pnfolder.pnlibrary.api.version.SemanticVersion

/** Structured explanation for a candidate or complete plan that cannot be installed. */
sealed class BlockedReason {
    /**
     * Product cannot run against the API generation required by the graph.
     *
     * @property product incompatible product
     * @property supportedApi API generations accepted by the product
     * @property requiredApi API generation required by the candidate graph
     * @property repository source repository, when known
     */
    data class ApiMismatch(
        val product: ProductId,
        val supportedApi: ApiVersionRange,
        val requiredApi: Int,
        val repository: String? = null,
    ) : BlockedReason()

    /**
     * Required managed product version is unavailable.
     *
     * @property product product declaring the dependency
     * @property dependency missing managed product
     * @property minimumVersion oldest acceptable dependency version
     */
    data class MissingDependency(
        val product: ProductId,
        val dependency: ProductId,
        val minimumVersion: SemanticVersion,
    ) : BlockedReason()

    /**
     * Required third-party plugin is absent or too old.
     *
     * @property product product declaring the dependency
     * @property plugin third-party plugin name
     * @property minimumVersion oldest acceptable plugin version
     * @property downloadPage optional administrator-facing download page
     */
    data class MissingExternalPluginDependency(
        val product: ProductId,
        val plugin: String,
        val minimumVersion: SemanticVersion,
        val downloadPage: String?,
    ) : BlockedReason()

    /**
     * Product updates are explicitly frozen.
     *
     * @property product frozen managed product
     */
    data class Frozen(val product: ProductId) : BlockedReason()

    /**
     * No published release satisfies the required API generation.
     *
     * @property product product without a compatible release
     * @property requiredApi API generation required by the candidate graph
     */
    data class NoCompatibleRelease(val product: ProductId, val requiredApi: Int) : BlockedReason()
}
