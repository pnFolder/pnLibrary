package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.updates.ProductChange
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot

/** Decides whether a resolved plan may be staged without an explicit user action. */
internal class UpdateAutomaticPolicy(
    private val configuration: UpdateConfiguration,
    private val registrations: () -> List<RegisteredUpdate>,
) {
    fun allows(snapshot: UpdatePlanSnapshot): Boolean {
        val changed = snapshot.plan?.changes?.map(ProductChange::product).orEmpty()
        return changed.all { component ->
            if (component.value == "pnlibrary") configuration.library.automaticDownload
            else {
                val policy = configuration.plugins[component.value]
                configuration.pluginUpdatesEnabled && policy?.enabled != false &&
                    (policy?.automaticDownload ?: (configuration.pluginAutomaticDownload ||
                        registrations().firstOrNull { it.descriptor.id == component }
                            ?.request?.automaticDownload == true))
            }
        }
    }
}
