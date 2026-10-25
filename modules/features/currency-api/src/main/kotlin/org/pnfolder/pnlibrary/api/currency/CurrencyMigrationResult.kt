package org.pnfolder.pnlibrary.api.currency

import java.time.Instant

/** Timing and import result produced by a cross-storage currency migration. */
data class CurrencyMigrationResult(
    /** Currency read from the source storage. */
    val sourceCurrency: CurrencyKey,
    /** Currency written in the target storage. */
    val targetCurrency: CurrencyKey,
    /** Detailed target import counters. */
    val import: CurrencyImportResult,
    /** Instant before export began. */
    val startedAt: Instant,
    /** Instant after target import completed. */
    val completedAt: Instant,
)
