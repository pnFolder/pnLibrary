package org.pnfolder.pnlibrary.api.updates

import org.pnfolder.pnlibrary.api.dependencies.PluginDependency
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest

/**
 * The entry to plugin updates: register a product, then use one of five focused parts.
 *
 * | Part | What it is for |
 * |---|---|
 * | [products] | which products are registered, and their state |
 * | [plans] | checking for releases and staging the resulting plan |
 * | [settings] | per-product choices: channel, automatic download, hold, Java build |
 * | [backups] | saved rollback sets, rolling back, restoring databases |
 * | [history] | the durable log of what happened |
 *
 * Every part is always present and every method works: there are no optional operations. Registration and lookup are
 * safe from any thread; checks are serialized by the runtime and concurrent checks share the one that is running.
 * Completion stages finish on the update executor, so callers must hand platform changes over to their platform or
 * task API. Passing an actor records who asked; it is audit information and grants no permission.
 */
interface UpdateService {
    /** Registers [owner]'s [product] and starts watching its releases at once. */
    fun register(
        owner: Any,
        product: ProductDescriptor,
        request: PluginUpdateRequest,
        dependencies: List<PluginDependency> = emptyList(),
    ): UpdateRegistration

    /** Registered products: list, find, state. */
    val products: UpdateProducts

    /** Release checks and update plans. */
    val plans: UpdatePlans

    /** Per-product update choices. */
    val settings: UpdateSettings

    /** Saved rollback sets and returning to them. */
    val backups: UpdateBackups

    /** What happened, newest first, kept across restarts. */
    val history: UpdateHistory
}
