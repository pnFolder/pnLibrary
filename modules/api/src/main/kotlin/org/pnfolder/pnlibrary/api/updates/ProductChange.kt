package org.pnfolder.pnlibrary.api.updates

import org.pnfolder.pnlibrary.api.version.SemanticVersion

/**
 * One version transition selected by the resolver.
 *
 * @property product product being installed or upgraded
 * @property from currently installed version, or `null` for a new product
 * @property to selected target version
 */
data class ProductChange(
    val product: ProductId,
    val from: SemanticVersion?,
    val to: SemanticVersion,
)
