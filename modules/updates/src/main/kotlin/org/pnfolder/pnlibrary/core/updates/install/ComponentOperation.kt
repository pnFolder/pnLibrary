package org.pnfolder.pnlibrary.core.updates.install

/** Operation names the component ledger writes to the update history. */
object ComponentOperation {
    const val ADDED = "COMPONENT_ADDED"
    const val REMOVED = "COMPONENT_REMOVED"
    const val VERSION_CHANGED = "VERSION_CHANGED"
    const val SETTINGS_CHANGED = "SETTINGS_CHANGED"
}
