package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.audience.delivery.DeliveryStatus
import org.pnfolder.pnlibrary.api.audience.delivery.Delivery
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult

/** Writes a warning and carries on when the platform could not deliver; output never fails a scenario. */
internal fun ActionContext.warnUnless(delivery: Delivery, what: String): ActionResult {
    when (delivery.status) {
        DeliveryStatus.DELIVERED -> Unit
        DeliveryStatus.UNSUPPORTED -> logger.warning("$what is not supported by the current platform")
        else -> logger.warning("$what failed: ${delivery.failure?.message ?: delivery.status}")
    }
    return ActionResult.Done
}
