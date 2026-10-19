package org.pnfolder.pnlibrary.api.updates

import org.pnfolder.pnlibrary.api.version.ApiVersionRange
import org.pnfolder.pnlibrary.api.version.SemanticVersion

/**
 * Installed component state used as the starting point for resolution.
 *
 * @property product installed product identity
 * @property version currently running version
 * @property supportedApi pnLibrary API generations accepted by this installation
 * @property providesApi API generation supplied by this product, when applicable
 */
data class InstalledProduct(
    val product: ProductId,
    val version: SemanticVersion,
    val supportedApi: ApiVersionRange,
    val providesApi: Int? = null,
) {
    init {
        require(providesApi == null || providesApi > 0) { "Provided API generation must be positive" }
    }
}
