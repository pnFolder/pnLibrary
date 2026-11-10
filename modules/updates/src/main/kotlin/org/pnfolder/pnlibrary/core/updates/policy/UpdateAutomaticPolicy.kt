package org.pnfolder.pnlibrary.core.updates.policy

import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.updates.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.release.ProductChange
import org.pnfolder.pnlibrary.core.updates.service.RegisteredUpdate

/** Decides whether a resolved plan may be staged without an explicit user action. */
internal class UpdateAutomaticPolicy(
    private val configuration: UpdateConfiguration,
    private val overrides: UpdatePolicyOverrides? = null,
    private val registrations: () -> List<RegisteredUpdate>,
) {
    /** Whether the plan may be installed without an administrator. */
    fun allows(snapshot: UpdatePlanSnapshot): Boolean {
        val changed = snapshot.plan?.changes?.map(ProductChange::product).orEmpty()
        return changed.all { component ->
            val chosen = overrides?.automatic(component.value)
            if (component.value == PnLibraryIdentity.PRODUCT_ID) chosen ?: configuration.library.automaticDownload
            else {
                chosen ?: overrides?.allPlugins() ?: (configuration.pluginAutomaticDownload ||
                    registrations().firstOrNull { it.descriptor.id == component }?.request?.automaticDownload == true)
            }
        }
    }
}
