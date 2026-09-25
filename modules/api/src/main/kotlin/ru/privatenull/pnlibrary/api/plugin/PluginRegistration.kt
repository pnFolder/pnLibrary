package ru.privatenull.pnlibrary.api.plugin

import java.util.Collections
import java.util.function.Consumer

/** Registration of one native platform plugin and all logical modules it owns. */
interface PluginRegistration : AutoCloseable {
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
    /** Returns an immutable snapshot of logical modules owned by this plugin. */
    @Suppress("DEPRECATION")
    fun all(): List<ModuleContext> = Collections.unmodifiableList(ArrayList(modules()))
    /** Compatibility alias for [all]. */
    @Deprecated("Use all()", ReplaceWith("all()"))
    fun modules(): List<ModuleContext>
    override fun close()
}
