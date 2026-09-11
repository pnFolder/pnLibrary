package ru.privatenull.pnlibrary.api.config

import java.util.function.Supplier
import java.util.function.Consumer

/** Creates plugin-owned typed configuration scopes. */
interface ConfigurationService {
    fun scope(owner: Any): ConfigScope
}

/** Typed configuration files tied to one plugin lifecycle. */
interface ConfigScope : AutoCloseable {
    fun <T : Any> yaml(path: String, type: Class<T>, defaults: Supplier<T>): ManagedConfig<T>
    fun <T : Any> yaml(path: String, type: Class<T>, defaults: Supplier<T>, options: ConfigOptions): ManagedConfig<T>
    fun <T : Any> serializer(type: Class<T>, serializer: ConfigSerializer<T>): ConfigScope
    fun <T : Any> type(
        baseType: Class<T>, implementation: Class<out T>, name: String,
        aliases: Set<String>, priority: Int, access: ConfigTypeAccess,
    ): ConfigTypeRegistration
    fun <T : Any> type(baseType: Class<T>, implementation: Class<out T>, name: String): ConfigTypeRegistration =
        type(baseType, implementation, name, emptySet(), 0, ConfigTypeAccess.ownerOnly())
    fun <T : Any> type(
        baseType: Class<T>, implementation: Class<out T>, name: String,
        aliases: Set<String>, priority: Int, access: Consumer<ConfigTypeAccess.Builder>,
    ): ConfigTypeRegistration = type(
        baseType, implementation, name, aliases, priority,
        ConfigTypeAccess.builder().also(access::accept).build(),
    )
    fun loadAll()
    fun reloadAll()
    fun saveAll()
    val size: Int
    override fun close()
}

/** Kotlin-friendly typed factory. Java callers use `Class` and `Supplier`. */
inline fun <reified T : Any> ConfigScope.yaml(path: String, noinline defaults: () -> T): ManagedConfig<T> =
    yaml(path, T::class.java, Supplier(defaults))

inline fun <reified T : Any> ConfigScope.yaml(
    path: String,
    options: ConfigOptions,
    noinline defaults: () -> T,
): ManagedConfig<T> = yaml(path, T::class.java, Supplier(defaults), options)

inline fun <reified T : Any> ConfigScope.serializer(serializer: ConfigSerializer<T>): ConfigScope =
    serializer(T::class.java, serializer)
