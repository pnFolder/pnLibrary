package org.pnfolder.pnlibrary.api.currency

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Portable, consistent export of one currency's balances and ledger.
 *
 * @property formatVersion serialization contract version; currently exactly `1`
 * @property currency currency represented by every contained record
 * @property createdAt export creation instant
 * @property balances stored balances keyed by player UUID
 * @property transactions ledger records in storage export order
 */
data class CurrencyStorageSnapshot(
    val formatVersion: Int = 1,
    val currency: CurrencyKey,
    val createdAt: Instant,
    val balances: Map<UUID, BigDecimal>,
    val transactions: List<CurrencyTransaction>,
) {
    init { require(formatVersion == 1) { "Unsupported currency snapshot version: $formatVersion" } }
}
