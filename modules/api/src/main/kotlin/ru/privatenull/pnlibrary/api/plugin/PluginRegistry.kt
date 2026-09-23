package ru.privatenull.pnlibrary.api.plugin

/** Registry of native platform plugins using one pnLibrary runtime. */
interface PluginRegistry : AutoCloseable {
    fun register(owner: Any): PluginRegistration
    fun get(owner: Any): PluginRegistration?
    fun require(owner: Any): PluginRegistration =
        get(owner) ?: error("Platform plugin ${owner.javaClass.name} is not registered in pnLibrary")
    fun unregister(owner: Any)
    fun registrations(): List<PluginRegistration>
    override fun close()
}
