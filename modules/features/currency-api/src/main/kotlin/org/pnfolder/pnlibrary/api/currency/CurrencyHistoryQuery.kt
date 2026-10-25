package org.pnfolder.pnlibrary.api.currency

import java.time.Instant
import java.util.UUID

/**
 * Filters and paginates reverse-chronological ledger history.
 *
 * All non-null filters are combined with logical AND. [from] is inclusive and [until]
 * is exclusive, making adjacent time ranges safe to concatenate without duplicates.
 * Empty [types] accepts every transaction type.
 *
 * @property account match transactions involving this player as source or target
 * @property actor match the exact initiating principal
 * @property service match the exact application service identifier
 * @property types accepted transaction types, or empty for every type
 * @property from inclusive lower timestamp boundary
 * @property until exclusive upper timestamp boundary
 * @property offset number of matching records to skip
 * @property limit maximum page size, from `1` through `500`
 */
data class CurrencyHistoryQuery(
    val account: UUID? = null,
    val actor: CurrencyActor? = null,
    val service: String? = null,
    val types: Set<CurrencyTransactionType> = emptySet(),
    val from: Instant? = null,
    val until: Instant? = null,
    val offset: Int = 0,
    val limit: Int = 50,
) {
    init {
        require(offset >= 0) { "Currency history offset must not be negative" }
        require(limit in 1..500) { "Currency history limit must be between 1 and 500" }
    }
}
