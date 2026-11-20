package org.pnfolder.pnlibrary.api.updates

import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry
import java.util.concurrent.CompletionStage

/** The durable log of updates: checks, downloads, installs, rollbacks and changed settings. */
interface UpdateHistory {
    /** Ten events newest first; [page] counts from one, [product] limits them to one managed product id. */
    fun page(page: Int, product: String? = null): CompletionStage<List<UpdateHistoryEntry>>

    /** How many events [page] pages through, for all products or for one [product]. */
    fun count(product: String? = null): CompletionStage<Int>
}
