package ru.privatenull.pnlibrary.api.updates

/**
 * Immutable atomic update target.
 *
 * @property targetApi pnLibrary API generation selected for the complete graph
 * @property changes version transitions required to apply the plan
 * @property selected complete release set chosen by the resolver
 */
data class UpdatePlan(
    val targetApi: Int,
    val changes: List<ProductChange>,
    val selected: List<ProductRelease>,
) {
    init {
        require(targetApi > 0) { "Target API generation must be positive" }
    }
}
