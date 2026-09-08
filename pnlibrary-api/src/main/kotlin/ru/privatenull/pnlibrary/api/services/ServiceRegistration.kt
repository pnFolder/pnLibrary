package ru.privatenull.pnlibrary.api.services

import ru.privatenull.pnlibrary.api.plugin.PluginId

/** Handle for one published service provider. */
interface ServiceRegistration<T : Any> : AutoCloseable {
    val owner: PluginId
    val type: Class<T>
    val service: T
    val priority: Int
    val isClosed: Boolean

    /** Removes this provider from the service manager. */
    override fun close()
}
