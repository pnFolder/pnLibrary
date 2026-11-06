package org.pnfolder.pnlibrary.core.updates.catalog

import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.core.updates.policy.UpdateDefaults
import java.util.UUID

/** The chat reminder of proxies: who gets it, how often, and the text. It reads known state and never asks GitHub. */
object ProxyUpdateReminder {
    val INTERVAL_SECONDS: Long = UpdateDefaults.REMINDER_INTERVAL.seconds

    fun canSee(hasPermission: (String) -> Boolean): Boolean = hasPermission("pnlibrary.admin") || hasPermission("pnlibrary.updates.notify")

    /** Sends the notice to [player] when an update is available or waits for a restart; sends nothing otherwise. */
    fun send(library: PnLibrary, player: UUID) {
        val audience = library.audiences.player(player) ?: return
        val snapshots = library.updates.all().map { it.snapshot }
        UpdateCatalogView.announcement(snapshots, ProxyUpdateCommand.ROUTES, PnLibraryIdentity.SUPPORT_URL).forEach(audience::sendMessage)
    }
}
