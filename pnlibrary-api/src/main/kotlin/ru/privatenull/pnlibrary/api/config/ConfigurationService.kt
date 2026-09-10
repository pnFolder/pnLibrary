package ru.privatenull.pnlibrary.api.config

import java.util.function.Supplier

/** Creates plugin-owned typed configuration scopes. */
interface ConfigurationService {
    fun scope(owner: Any): ConfigScope
}

/** Typed configuration files tied to one plugin lifecycle. */
interface ConfigScope : AutoCloseable {
    fun <T : Any> yaml(path: String, type: Class<T>, defaults: Supplier<T>): ManagedConfig<T>
    fun loadAll()
    fun reloadAll()
    fun saveAll()
    val size: Int
    override fun close()
}

/** Kotlin-friendly typed factory. Java callers use `Class` and `Supplier`. */
inline fun <reified T : Any> ConfigScope.yaml(path: String, noinline defaults: () -> T): ManagedConfig<T> =
    yaml(path, T::class.java, Supplier(defaults))
