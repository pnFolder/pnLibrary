package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.logging.LogLevel

/** Native plugin ownership and metadata operations used by the shared engine. */
interface PlatformOwnership : PlatformLogging {
    fun ownerMetadata(owner: Any): PluginSnapshot? = null

    fun unsupportedOwner(owner: Any): PluginSnapshot? {
        log(owner, LogLevel.WARNING,
            "Не удалось получить метаданные плагина: объект ${owner.javaClass.name} " +
                "не является поддерживаемым плагином. Операция пропущена; работа продолжается.")
        return null
    }

    fun ownerDetails(owner: Any): Map<String, String> = ownerMetadata(owner)?.let {
        mapOf("id" to it.id, "name" to it.name, "version" to it.version,
            "authors" to it.authors.joinToString(", "))
    }.orEmpty()

    fun remotePolicyContext(owner: Any, metadata: PluginMetadata, values: Map<String, String>): RemotePolicyContext =
        throw UnsupportedOperationException("Remote policy is unavailable")

    fun disableOwner(owner: Any): Boolean = false
    fun acceptsOwner(owner: Any): Boolean = ownerDetails(owner).isNotEmpty()
    fun installedPlugins(): Map<String, String> = emptyMap()
}
