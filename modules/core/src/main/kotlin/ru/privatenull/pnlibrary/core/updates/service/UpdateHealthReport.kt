package ru.privatenull.pnlibrary.core.updates.service

import ru.privatenull.pnlibrary.api.updates.ProductChange
import java.util.UUID

/** Observed loaded versions for one durable transaction, independent of the current release plan. */
internal data class UpdateHealthReport(
    val transactionId: UUID,
    val planId: UUID,
    val changes: List<ProductChange>,
    val healthy: Boolean,
    val rollback: Boolean,
    val message: String,
)
