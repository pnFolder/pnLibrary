package ru.privatenull.pnlibrary.api.plugin

/** Registry of native platform plugins using one pnLibrary runtime. */
interface PluginRegistry : AutoCloseable {
    fun register(owner: Any): PluginContext
    fun get(owner: Any): PluginContext?
    fun require(owner: Any): PluginContext =
        get(owner) ?: error("Platform plugin ${owner.javaClass.name} is not registered in pnLibrary")
    fun unregister(owner: Any)
    fun registrations(): List<PluginContext>
    override fun close()
}
