package ru.privatenull.pnlibrary.api.plugin

import java.util.function.Consumer

/** Owns all logical pnLibrary modules exposed by one native platform plugin. */
interface PluginContext : AutoCloseable {
    val owner: Any
    val isClosed: Boolean

    fun registerModule(
        id: ModuleId,
        configure: Consumer<PluginBuilder> = Consumer { },
    ): ModuleContext

    fun registerModule(
        id: String,
        configure: Consumer<PluginBuilder> = Consumer { },
    ): ModuleContext = registerModule(ModuleId.of(id), configure)

    fun getModule(id: ModuleId): ModuleContext?
    fun getModule(id: String): ModuleContext? = getModule(ModuleId.of(id))
    fun requireModule(id: ModuleId): ModuleContext =
        getModule(id) ?: error("Module $id is not registered for this plugin")
    fun requireModule(id: String): ModuleContext = requireModule(ModuleId.of(id))
    fun unregisterModule(id: ModuleId)
    fun unregisterModule(id: String) = unregisterModule(ModuleId.of(id))
    fun modules(): List<ModuleContext>
    override fun close()
}
