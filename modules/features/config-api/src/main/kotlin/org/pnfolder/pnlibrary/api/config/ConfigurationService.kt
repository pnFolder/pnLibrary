package org.pnfolder.pnlibrary.api.config

import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.nio.file.Path
import java.util.function.Supplier

/**
 * Creates lifecycle-bound typed configuration scopes and lets you look up every registered file.
 *
 * The same owner object resolves to the same scope. Owner identity is used rather than equality.
 * A file is counted from the owner's data folder by default and cannot escape its [ConfigRoot].
 * Every registered file has an identifier `plugin/module:path`, for example `quests/main:quests.yml`.
 */
interface ConfigurationService {
    /** Returns the existing scope for [owner], or creates one atomically. */
    fun scope(owner: Any): ConfigScope

    /** Every registered file of every plugin, in registration order. */
    fun all(): List<ConfigInfo>

    /** The files inside [address]: a whole plugin, one module or one file. */
    fun find(address: ConfigAddress): List<ConfigInfo>

    /** The files inside the address written as text: `quests`, `quests/main` or `quests/main:quests.yml`. */
    fun find(address: String): List<ConfigInfo> = find(ConfigAddress.parse(address))

    /** The file with identifier [id], or `null` when it is not registered. */
    fun get(id: ConfigId): ManagedConfig<*>?

    /** The file with the identifier written as text, or `null` when it is not registered. */
    fun get(id: String): ManagedConfig<*>? = get(ConfigId.parse(id))

    /** The file with identifier [id], or an error naming the registered files. */
    fun require(id: ConfigId): ManagedConfig<*> =
        get(id) ?: error("Configuration $id is not registered; registered: ${all().joinToString { it.id.toString() }}")

    /** The file with the identifier written as text, or an error naming the registered files. */
    fun require(id: String): ManagedConfig<*> = require(ConfigId.parse(id))

    /** The registered files that read into [model]. */
    fun byModel(model: Class<*>): List<ConfigInfo> = all().filter { it.modelType == model }

    /** Every word [baseType] understands: the ones its annotations declare and the ones plugins added. */
    fun types(baseType: Class<*>): List<ConfigTypeInfo>

    /** The serializers that plugins added, with where each one works. Built-in ones are not listed. */
    fun serializers(): List<ConfigSerializerInfo>
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
    fun <T : Any> yaml(path: String, type: Class<T>, defaults: Supplier<T>, options: ConfigOptions): ManagedConfig<T> =
        yaml(path, type, defaults, options, ConfigValueValidator { emptyList() })

    /**
     * Registers a file at any place on the machine, for example a path the plugin read from its own settings.
     * The file is the plugin's own: it is created when missing and rewritten like one in the data folder.
     * Its identifier is `plugin/module:@external/<absolute path>`.
     */
    fun <T : Any> yamlAt(file: Path, type: Class<T>, defaults: Supplier<T>, options: ConfigOptions = ConfigOptions.DEFAULT): ManagedConfig<T> =
        yaml(file.toAbsolutePath().normalize().toString(), type, defaults, options.copy(root = ConfigRoot.EXTERNAL))

    /**
     * Registers a typed YAML file with an additional programmatic [validator].
     *
     * Annotation rules such as `@ConfigRange` and the [validator] both run on every load, reload
     * and save; their problems are reported together in one [ConfigValidationException].
     */
    fun <T : Any> yaml(
        path: String,
        type: Class<T>,
        defaults: Supplier<T>,
        options: ConfigOptions,
        validator: ConfigValueValidator<T>,
    ): ManagedConfig<T>

    /**
     * Registers or replaces the scope-local serializer for [type].
     *
     * It works in the files of this module only; use [serializerFor] to work in other files.
     * A file is read with the serializers it has at the time of the read.
     */
    fun <T : Any> serializer(type: Class<T>, serializer: ConfigSerializer<T>): ConfigScope

    /**
     * Starts publishing [serializer] for [type], to work in this module or, with `into`, in other files.
     *
     * ```kotlin
     * // Every file of the plugin `shop` writes a Point as one line
     * scope.serializerFor(Point::class.java, PointSerializer()).into("shop").register()
     *
     * // Every file of every plugin
     * scope.serializerFor(Point::class.java, PointSerializer()).everywhere().register()
     * ```
     *
     * The file's own module keeps its serializers: an addition only fills what the module has none for.
     * A module can refuse additions with [extensions]. Files that are already read keep their value until read again.
     * Nothing is published until [ConfigSerializerBuilder.register] is called.
     */
    fun <T : Any> serializerFor(type: Class<T>, serializer: ConfigSerializer<T>): ConfigSerializerBuilder<T> =
        ConfigSerializerBuilder(this, type, serializer)

    /**
     * Low-level publication behind [serializerFor]; prefer the builder.
     *
     * [target] `null` means the files of this scope's module. For files of other modules the type must not be a
     * primitive or come from `java.`, `javax.`, `kotlin.` or `jdk.`, so that nobody rewrites how ordinary values are read.
     */
    fun <T : Any> publishSerializer(
        type: Class<T>,
        serializer: ConfigSerializer<T>,
        priority: Int,
        target: ConfigAddress?,
    ): ConfigSerializerRegistration

    /**
     * Starts publishing one implementation of a polymorphic configuration [baseType].
     *
     * ```kotlin
     * // Only my own files understand `type: message`
     * scope.type(Action::class.java, MessageAction::class.java).name("message").alias("msg").register()
     *
     * // Teach another plugin's file the word: `type: message` works in quests/main:quests.yml
     * scope.type(Action::class.java, MessageAction::class.java).name("message").into("quests/main:quests.yml").register()
     * ```
     *
     * Nothing is published until [ConfigTypeBuilder.register] is called.
     */
    fun <T : Any> type(baseType: Class<T>, implementation: Class<out T>): ConfigTypeBuilder<T> =
        ConfigTypeBuilder(this, baseType, implementation)

    /**
     * Low-level publication behind [type]; prefer the builder.
     *
     * [name] and [aliases] are case-insensitive identifiers used in the discriminator field.
     * Higher [priority] wins when multiple visible registrations accept the same identifier.
     * [target] says where the word works; `null` means the files of this scope's module. The word cannot
     * take a name that the base type declares itself or that the target plugin has published.
     * A module can refuse additions with [extensions].
     */
    fun <T : Any> publishType(
        baseType: Class<T>,
        implementation: Class<out T>,
        name: String,
        aliases: Set<String>,
        priority: Int,
        target: ConfigAddress?,
        conflict: ConfigTypeConflict = ConfigTypeConflict.FAIL,
    ): ConfigTypeRegistration

    /** Plugin that owns this scope. */
    val plugin: PluginId

    /** Module that owns this scope. */
    val module: ModuleId

    /** The files registered in this scope, in registration order. */
    fun list(): List<ConfigInfo>

    /** The file registered under [path] (relative to the plugin folder, or starting with `@plugins/` or `@server/`), or `null`. */
    fun get(path: String): ManagedConfig<*>?

    /** The file registered under [path], or an error naming the registered files. */
    fun require(path: String): ManagedConfig<*> =
        get(path) ?: error("Configuration $path is not registered in $plugin/$module; registered: ${list().joinToString { it.id.path }}")

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
 * Registers a typed YAML file with explicit [options] and an extra programmatic [validator].
 *
 * ```kotlin
 * val validator = ConfigValidatorBuilder<Settings>()
 *     .require("database.port", "must be between 1 and 65535") { it.database.port in 1..65535 }
 *     .build()
 * val settings = scope.yaml("settings.yml", ConfigOptions.DEFAULT, validator) { Settings() }
 * ```
 */
inline fun <reified T : Any> ConfigScope.yaml(
    path: String,
    options: ConfigOptions,
    validator: ConfigValueValidator<T>,
    noinline defaults: () -> T,
): ManagedConfig<T> = yaml(path, T::class.java, Supplier(defaults), options, validator)

/**
 * Starts publishing implementation [I] of polymorphic base type [B] while inferring both classes.
 *
 * ```kotlin
 * scope.type<Action, MessageAction>().name("message").register()
 * ```
 */
inline fun <reified B : Any, reified I : B> ConfigScope.type(): ConfigTypeBuilder<B> =
    type(B::class.java, I::class.java)

/**
 * Registers [serializer] for its inferred value type in this scope.
 *
 * ```kotlin
 * scope.serializer(DurationSerializer())
 * ```
 */
inline fun <reified T : Any> ConfigScope.serializer(serializer: ConfigSerializer<T>): ConfigScope =
    serializer(T::class.java, serializer)

/**
 * Starts publishing [serializer] for its inferred value type.
 *
 * ```kotlin
 * scope.serializerFor(PointSerializer()).into("shop/main").register()
 * ```
 */
inline fun <reified T : Any> ConfigScope.serializerFor(serializer: ConfigSerializer<T>): ConfigSerializerBuilder<T> =
    serializerFor(T::class.java, serializer)

/** Kotlin form of [ConfigScope.yamlAt]: `scope.yamlAt<Settings>(path) { Settings() }`. */
inline fun <reified T : Any> ConfigScope.yamlAt(
    file: Path,
    options: ConfigOptions = ConfigOptions.DEFAULT,
    noinline defaults: () -> T,
): ManagedConfig<T> = yamlAt(file, T::class.java, Supplier(defaults), options)
