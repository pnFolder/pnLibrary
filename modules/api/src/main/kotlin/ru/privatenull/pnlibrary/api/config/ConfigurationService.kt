package ru.privatenull.pnlibrary.api.config

import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Creates lifecycle-bound typed configuration scopes.
 *
 * The same owner object resolves to the same scope. Owner identity is used rather than equality.
 * Files are rooted in the owner's platform data directory and cannot escape it.
 */
interface ConfigurationService {
    /** Returns the existing scope for [owner], or creates one atomically. */
    fun scope(owner: Any): ConfigScope
}

/**
 * Typed YAML configuration files and serializers tied to one plugin lifecycle.
 *
 * ```kotlin
 * val settings = library.configurations.scope(plugin).yaml<Settings>("config.yml") {
 *     Settings()
 * }
 * val loaded = settings.load().value
 * ```
 *
 * Closing the scope unloads every managed file and removes its dynamically published types.
 * It never deletes configuration files.
 */
interface ConfigScope : AutoCloseable {
    /** Registers a `.yml` or `.yaml` file using [ConfigOptions.DEFAULT]. */
    fun <T : Any> yaml(path: String, type: Class<T>, defaults: Supplier<T>): ManagedConfig<T>

    /**
     * Registers a typed YAML file relative to the plugin data directory.
     *
     * [path] must be relative, remain inside the data directory after normalization, and end in
     * `.yml` or `.yaml`. One scope cannot register the same normalized file twice. [defaults] is
     * evaluated while the handle is created and must return a non-null value.
     */
    fun <T : Any> yaml(path: String, type: Class<T>, defaults: Supplier<T>, options: ConfigOptions): ManagedConfig<T>

    /**
     * Registers or replaces the scope-local serializer for [type].
     *
     * Register serializers before creating affected YAML handles because each handle snapshots
     * the available serializers at creation time.
     */
    fun <T : Any> serializer(type: Class<T>, serializer: ConfigSerializer<T>): ConfigScope

    /**
     * Publishes one implementation of a polymorphic configuration [baseType].
     *
     * [name] and [aliases] are case-insensitive identifiers used in the discriminator field.
     * Higher [priority] wins when multiple visible registrations accept the same identifier, and
     * [access] controls which plugins may consume the type.
     */
    fun <T : Any> type(
        baseType: Class<T>,
        implementation: Class<out T>,
        name: String,
        aliases: Set<String>,
        priority: Int,
        access: ConfigTypeAccess,
    ): ConfigTypeRegistration

    /** Publishes a private, zero-priority polymorphic implementation without aliases. */
    fun <T : Any> type(baseType: Class<T>, implementation: Class<out T>, name: String): ConfigTypeRegistration =
        type(baseType, implementation, name, emptySet(), 0, ConfigTypeAccess.ownerOnly())

    /** Publishes a polymorphic type using a Java-friendly access-policy callback. */
    fun <T : Any> type(
        baseType: Class<T>,
        implementation: Class<out T>,
        name: String,
        aliases: Set<String>,
        priority: Int,
        access: Consumer<ConfigTypeAccess.Builder>,
    ): ConfigTypeRegistration = type(
        baseType,
        implementation,
        name,
        aliases,
        priority,
        ConfigTypeAccess.builder().also(access::accept).build(),
    )

    /** Loads every registered file in registration order. */
    fun loadAll()

    /** Reloads every registered file while each handle preserves its prior value on failure. */
    fun reloadAll()

    /** Saves every currently loaded file in registration order. */
    fun saveAll()

    /** Number of managed YAML handles currently registered in this scope. */
    val size: Int

    /** Unloads all files and removes dynamic types owned by this scope. */
    override fun close()
}

/**
 * Registers a typed YAML file while inferring its model class.
 *
 * ```kotlin
 * val settings = scope.yaml("settings.yml") { Settings() }
 * ```
 *
 * Java callers use the `Class` and `Supplier` overload on [ConfigScope].
 */
inline fun <reified T : Any> ConfigScope.yaml(path: String, noinline defaults: () -> T): ManagedConfig<T> =
    yaml(path, T::class.java, Supplier(defaults))

/**
 * Registers a typed YAML file with explicit synchronization [options].
 *
 * ```kotlin
 * val settings = scope.yaml("settings.yml", ConfigOptions.DEFAULT) { Settings() }
 * ```
 */
inline fun <reified T : Any> ConfigScope.yaml(
    path: String,
    options: ConfigOptions,
    noinline defaults: () -> T,
): ManagedConfig<T> = yaml(path, T::class.java, Supplier(defaults), options)

/**
 * Registers [serializer] for its inferred value type in this scope.
 *
 * ```kotlin
 * scope.serializer(DurationSerializer())
 * ```
 */
inline fun <reified T : Any> ConfigScope.serializer(serializer: ConfigSerializer<T>): ConfigScope =
    serializer(T::class.java, serializer)
