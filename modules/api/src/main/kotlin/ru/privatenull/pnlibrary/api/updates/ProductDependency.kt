package ru.privatenull.pnlibrary.api.updates

import ru.privatenull.pnlibrary.api.version.SemanticVersion

/**
 * Minimum semantic version required from another managed component.
 *
 * @property product required managed product
 * @property minimumVersion oldest acceptable product version
 */
data class ProductDependency(
    val product: ProductId,
    val minimumVersion: SemanticVersion,
)
