package org.pnfolder.pnlibrary.core.updates.policy

import org.pnfolder.pnlibrary.api.updates.release.ProductChange
import org.pnfolder.pnlibrary.api.updates.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.core.updates.service.RegisteredUpdate

/** Decides whether a resolved plan may be staged without an explicit user action. */
internal class UpdateAutomaticPolicy(
    private val configuration: UpdateConfiguration,
    private val overrides: UpdatePolicyOverrides? = null,
    private val registrations: () -> List<RegisteredUpdate>,
) {
    fun allows(snapshot: UpdatePlanSnapshot): Boolean {
        val changed = snapshot.plan?.changes?.map(ProductChange::product).orEmpty()
        return changed.all { component ->
            val chosen = overrides?.automatic(component.value)
            if (component.value == "pnlibrary") chosen ?: configuration.library.automaticDownload
            else {
                configuration.pluginUpdatesEnabled && component.value !in configuration.ignoredPlugins &&
                    (chosen ?: (configuration.pluginAutomaticDownload ||
                        registrations().firstOrNull { it.descriptor.id == component }
                            ?.request?.automaticDownload == true))
            }
        }
    }
}
