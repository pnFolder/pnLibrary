package org.pnfolder.pnlibrary.api.updates

import org.pnfolder.pnlibrary.api.dependencies.PluginDependency
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup
import org.pnfolder.pnlibrary.api.updates.backup.UpdateHistoryEntry
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Registers and queries plugin update monitors owned by this runtime.
 *
 * Registration and lookup are safe from arbitrary threads. Graph checks are serialized by the
 * runtime and concurrent [checkNow] calls share the active check. Completion stages finish on the
 * update executor; callers must dispatch platform mutations through their platform/task API.
 */
interface UpdateService {
    /** Registers and immediately starts monitoring [product] represented by [owner]. */
    fun register(
        owner: Any,
        product: ProductDescriptor,
        request: PluginUpdateRequest,
        dependencies: List<PluginDependency> = emptyList(),
    ): UpdateRegistration

    /** Java-friendly overload for registering a product without dependencies. */
    fun register(owner: Any, product: ProductDescriptor, request: PluginUpdateRequest): UpdateRegistration =
        register(owner, product, request, emptyList())

    /** Returns an immutable snapshot of current registrations. */
    fun all(): List<UpdateRegistration>

    /** Finds a registration by product or repository name, ignoring case. */
    fun get(product: String): UpdateRegistration?

    /** Returns a registration or fails with the requested product in the message. */
    fun require(product: String): UpdateRegistration =
        get(product) ?: error("Update registration '$product' is unavailable")

    /** Refreshes every registered catalogue and resolves one complete compatibility plan. */
    fun checkNow(): CompletionStage<UpdatePlanSnapshot> = unsupported("graph update checks")

    /** Checks releases with explicit audit attribution. Does not grant permissions. */
    fun checkNow(actor: UpdateActor): CompletionStage<UpdatePlanSnapshot> = checkNow()

    /** Returns the latest immutable graph plan, when one has been resolved. */
    fun currentPlan(): Optional<UpdatePlanSnapshot> = Optional.empty()

    /** Downloads and verifies every artifact in the selected plan. */
    fun stage(planId: UUID): CompletionStage<UpdatePlanSnapshot> = unsupported("graph update staging")

    /** Stages exactly [planId] and records its initiator. Does not grant permissions. */
    fun stage(planId: UUID, actor: UpdateActor): CompletionStage<UpdatePlanSnapshot> = stage(planId)

    /** Confirms a token-bound sensitive action for the selected plan. */
    fun confirm(planId: UUID, token: String): CompletionStage<UpdatePlanSnapshot> = unsupported("graph update confirmation")

    /** Returns a bounded, newest-first, immutable graph-plan snapshot. */
    fun history(): List<UpdatePlanSnapshot> = emptyList()

    /**
     * Reads ten newest-first durable events on the update executor.
     * [page] is one-based; [product] is an optional normalized managed product ID.
     * Completion callbacks must dispatch platform operations to the appropriate platform thread.
     */
    fun historyPage(page: Int, product: String? = null): CompletionStage<List<UpdateHistoryEntry>> =
        CompletableFuture<List<UpdateHistoryEntry>>().also {
            it.completeExceptionally(UnsupportedOperationException("Update service does not support durable history"))
        }

    /** Counts the durable events [historyPage] pages through, optionally for one managed [product]. */
    fun historyCount(product: String? = null): CompletionStage<Int> =
        CompletableFuture<Int>().also {
            it.completeExceptionally(UnsupportedOperationException("Update service does not support durable history"))
        }

    /**
     * Switches the release [channel] of a managed [product] immediately. The choice is remembered across restarts
     * and takes precedence over the channel the plugin declares itself. A `null` [channel] removes the choice, so
     * the plugin's own channel applies again. Does not grant permissions.
     */
    fun setChannel(product: String, channel: UpdateChannel?, actor: UpdateActor): CompletionStage<UpdateSnapshot> =
        unsupportedSetting("channel changes")

    /** Turns automatic download of verified updates for [product] on or off, with the same persistence as [setChannel]. */
    fun setAutomaticDownload(product: String, enabled: Boolean, actor: UpdateActor): CompletionStage<UpdateSnapshot> =
        unsupportedSetting("automatic download changes")

    /** Holds updates of [product] for seven days, or releases a hold. */
    fun setHeld(product: String, held: Boolean, actor: UpdateActor): CompletionStage<UpdateSnapshot> =
        setHeld(product, if (held) Duration.ofDays(7) else null, actor)

    /**
     * Holds updates of [product] for [duration], from one minute to seven days, replacing any earlier hold; null
     * releases it. A held component is neither offered nor downloaded until the hold ends by itself.
     */
    fun setHeld(product: String, duration: Duration?, actor: UpdateActor): CompletionStage<UpdateSnapshot> =
        unsupportedSetting("holding updates")

    /** When the current hold of [product] ends, or null when its updates are not held. */
    fun heldUntil(product: String): Instant? = null

    /** Restores the previous JAR set from the newest rollback-capable transaction. */
    fun rollback(): CompletionStage<UpdatePlanSnapshot> = unsupported("manual update rollback")

    /** Requests rollback with explicit audit attribution. Does not grant permissions. */
    fun rollback(actor: UpdateActor): CompletionStage<UpdatePlanSnapshot> = rollback()

    /** Reads the retained complete rollback sets asynchronously, newest first when dates are known. */
    fun backups(): CompletionStage<List<UpdateBackup>> = CompletableFuture<List<UpdateBackup>>().also {
        it.completeExceptionally(UnsupportedOperationException("Update service does not support saved rollback sets"))
    }

    /** Prepares exactly [transactionId] for restart. Never substitutes another backup. */
    fun rollback(transactionId: UUID, actor: UpdateActor): CompletionStage<UpdatePlanSnapshot> =
        unsupported("selected transaction rollback")

    /** Explicitly opts into restoring the selected configuration snapshot at the next offline application. */
    fun rollback(transactionId: UUID, restoreConfigurations: Boolean, actor: UpdateActor): CompletionStage<UpdatePlanSnapshot> =
        if (restoreConfigurations) unsupported("configuration rollback") else rollback(transactionId, actor)

    private fun unsupportedSetting(operation: String): CompletionStage<UpdateSnapshot> =
        CompletableFuture<UpdateSnapshot>().also {
            it.completeExceptionally(UnsupportedOperationException("Update service does not support $operation"))
        }

    private fun unsupported(operation: String): CompletionStage<UpdatePlanSnapshot> =
        CompletableFuture<UpdatePlanSnapshot>().also {
            it.completeExceptionally(UnsupportedOperationException("Update service does not support $operation"))
        }
}
