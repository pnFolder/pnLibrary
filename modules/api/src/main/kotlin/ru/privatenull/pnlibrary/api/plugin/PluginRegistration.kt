package ru.privatenull.pnlibrary.api.plugin

import java.util.Collections
import java.util.function.Consumer

/** Registration of one native platform plugin and all logical modules it owns. */
interface PluginRegistration : AutoCloseable {
    /** Native platform plugin owning this registration. */
    val owner: Any
    /** Whether this registration has released its modules. */
    val isClosed: Boolean

    /** Registers a logical module identified by [id]. */
    fun registerModule(
        id: ModuleId,
        configure: Consumer<PluginBuilder> = Consumer { },
    ): ModuleContext

    /** Normalizes [id] and registers a logical module. */
    fun registerModule(
        id: String,
        configure: Consumer<PluginBuilder> = Consumer { },
    ): ModuleContext = registerModule(ModuleId.of(id), configure)

    /** Returns the module identified by [id], or `null` when absent. */
    fun getModule(id: ModuleId): ModuleContext?

    /** Normalizes [id] and returns the matching module, when present. */
    fun getModule(id: String): ModuleContext? = getModule(ModuleId.of(id))

    /** Returns the module identified by [id] or fails when absent. */
    fun requireModule(id: ModuleId): ModuleContext =
        getModule(id) ?: error("Module $id is not registered for this plugin")

    /** Normalizes [id] and returns the matching module or fails. */
    fun requireModule(id: String): ModuleContext = requireModule(ModuleId.of(id))

    /** Closes and unregisters the module identified by [id]. */
    fun unregisterModule(id: ModuleId)

    /** Normalizes [id], then closes and unregisters the matching module. */
    fun unregisterModule(id: String) = unregisterModule(ModuleId.of(id))

    /** Returns an immutable snapshot of logical modules owned by this plugin. */
    @Suppress("DEPRECATION")
    fun all(): List<ModuleContext> = Collections.unmodifiableList(ArrayList(modules()))

    /** Compatibility alias for [all]. */
    @Deprecated("Use all()", ReplaceWith("all()"))
    fun modules(): List<ModuleContext>

    /** Closes every owned module and releases this registration. */
    override fun close()
}
