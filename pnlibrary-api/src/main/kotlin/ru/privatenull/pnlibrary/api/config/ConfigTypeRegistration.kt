package ru.privatenull.pnlibrary.api.config

import ru.privatenull.pnlibrary.api.plugin.PluginId

/** Controls which plugin configurations may use a dynamically published type. */
class ConfigTypeAccess private constructor(
    val allLibraryPlugins: Boolean,
    val allowedPlugins: Set<PluginId>,
) {
    fun allows(owner: PluginId, consumer: PluginId): Boolean =
        owner == consumer || allLibraryPlugins || consumer in allowedPlugins

    companion object {
        @JvmStatic fun ownerOnly() = ConfigTypeAccess(false, emptySet())
        @JvmStatic fun everyone() = ConfigTypeAccess(true, emptySet())
        @JvmStatic fun plugins(vararg pluginIds: String) =
            ConfigTypeAccess(false, pluginIds.map(PluginId::of).toSet())
    }
}

/** Handle for one plugin-owned polymorphic configuration type. */
interface ConfigTypeRegistration : AutoCloseable {
    val owner: PluginId
    val baseType: Class<*>
    val implementation: Class<*>
    val name: String
    val isActive: Boolean
    override fun close()
}
