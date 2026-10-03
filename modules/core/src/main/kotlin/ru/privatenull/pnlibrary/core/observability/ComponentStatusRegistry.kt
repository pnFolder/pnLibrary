package ru.privatenull.pnlibrary.core.observability

import ru.privatenull.pnlibrary.api.observability.ComponentStatus

internal class ComponentStatusRegistry {
    private val lock = Any()
    private val statuses = linkedMapOf<ComponentKey, ComponentStatus>()

    fun update(status: ComponentStatus): ComponentStatus = synchronized(lock) {
        statuses[ComponentKey(status.plugin, status.component)] = status
        status
    }

    fun snapshot(): List<ComponentStatus> = synchronized(lock) { statuses.values.toList() }

    private data class ComponentKey(val plugin: String, val component: String)
}
