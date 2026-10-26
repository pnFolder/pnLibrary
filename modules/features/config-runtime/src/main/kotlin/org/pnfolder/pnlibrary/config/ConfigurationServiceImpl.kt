package org.pnfolder.pnlibrary.config

import org.pnfolder.pnlibrary.api.config.ConfigOptions
import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.config.ConfigSerializer
import org.pnfolder.pnlibrary.api.config.ConfigTypeAccess
import org.pnfolder.pnlibrary.api.config.ConfigTypeRegistration
import org.pnfolder.pnlibrary.api.config.ConfigValueValidator
import org.pnfolder.pnlibrary.api.config.ManagedConfig
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.common.lang.Failures
import org.pnfolder.pnlibrary.config.yaml.AnnotatedYamlCodec
import org.pnfolder.pnlibrary.config.yaml.CodeFirstYaml
import org.pnfolder.pnlibrary.config.yaml.RuntimeConfigType
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.IdentityHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import java.util.logging.Logger

/** Hands out one configuration scope per plugin, rooted at its data folder, and closes them with their owner. */
class ConfigurationServiceImpl(
    private val platform: PlatformOwnership,
) : ConfigurationRuntime {
    private val scopes = IdentityHashMap<Any, MutableMap<PluginId, Scope>>()
    private val runtimeTypes = linkedSetOf<TypeRegistration>()
    private val closed = AtomicBoolean(false)

    /** Returns the scope of [owner], using the plugin id the platform reports for it. */
    override fun scope(owner: Any): ConfigScope {
        val pluginId = PluginId.of(platform.ownerDetails(owner)["id"] ?: owner.javaClass.simpleName)
        return scope(owner, pluginId)
    }

    /** Returns the scope of [owner] and [pluginId], creating it on first use. Fails after [close]. */
    override fun scope(owner: Any, pluginId: PluginId): ConfigScope = synchronized(scopes) {
        check(!closed.get()) { "Configuration service is closed" }
        scopes.getOrPut(owner) { linkedMapOf() }.getOrPut(pluginId) {
            Scope(
                owner = owner,
                pluginId = pluginId,
                directory = ownerDirectory(owner).toAbsolutePath().normalize(),
                logger = ownerLogger(owner),
            )
        }
    }

    /** Closes and forgets every scope that [owner] created. */
    override fun close(owner: Any) {
        synchronized(scopes) { scopes.remove(owner)?.values?.toList().orEmpty() }.forEach { it.close() }
    }

    /** Closes every scope and rejects further [scope] calls. Repeated calls do nothing. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(scopes) {
            scopes.values.flatMap { it.values }.also { scopes.clear() }
        }.forEach { it.close() }
    }

    private fun ownerDirectory(owner: Any): Path = try {
        val value = owner.javaClass.getMethod("getDataFolder").invoke(owner)
        (value as File).toPath()
    } catch (_: ReflectiveOperationException) {
        // Owners without a Bukkit-style data folder use the conventional plugins/<id> directory.
        val id = platform.ownerDetails(owner)["id"] ?: owner.javaClass.simpleName
        Paths.get("plugins", id)
    }

    private fun ownerLogger(owner: Any): Logger = try {
        owner.javaClass.getMethod("getLogger").invoke(owner) as Logger
    } catch (_: Exception) {
        // Owners without their own logger get a JUL logger named after the owner.
        Logger.getLogger(platform.ownerDetails(owner)["id"] ?: owner.javaClass.name)
    }

    private inner class Scope(
        private val owner: Any,
        private val pluginId: PluginId,
        private val directory: Path,
        private val logger: Logger,
    ) : ConfigScope {
        private val handles = linkedSetOf<ManagedConfig<*>>()
        private val serializers = BuiltInConfigSerializers.create()
        private val scopeClosed = AtomicBoolean(false)
        override val size: Int get() = synchronized(handles) { handles.size }

        override fun <T : Any> yaml(path: String, type: Class<T>, defaults: Supplier<T>): ManagedConfig<T> =
            yaml(path, type, defaults, ConfigOptions.DEFAULT)

        override fun <T : Any> yaml(
            path: String,
            type: Class<T>,
            defaults: Supplier<T>,
            options: ConfigOptions,
            validator: ConfigValueValidator<T>,
        ): ManagedConfig<T> {
            check(!scopeClosed.get()) { "Configuration scope is closed" }
            val relative = Paths.get(path).normalize()
            require(!relative.isAbsolute && !relative.startsWith("..")) {
                "Configuration path must stay inside the plugin directory"
            }
            require(path.endsWith(".yml", true) || path.endsWith(".yaml", true)) {
                "Configuration file must use .yml or .yaml"
            }
            val target = directory.resolve(relative).normalize()
            require(target.startsWith(directory)) { "Configuration path escapes the plugin directory" }
            val defaultValue = defaults.get() ?: error("Configuration defaults cannot be null")
            val codec = AnnotatedYamlCodec(
                type = type,
                defaults = defaults,
                serializers = synchronized(serializers) { serializers.toMap() },
                runtimeTypes = { visibleTypes(pluginId) },
                consumer = pluginId.value,
                options = options,
                warning = { message -> logger.warning(message) },
            )
            val handle = CodeFirstYaml(
                file = target.toFile(),
                defaults = defaultValue,
                codec = codec,
                logger = logger,
                validator = ConfigValueValidator { value -> codec.validate(value) + validator.validate(value) },
                options = options,
            )
            val owned = OwnedConfig(target, handle)
            synchronized(handles) {
                check(!scopeClosed.get()) { "Configuration scope is closed" }
                check(handles.none { (it as? OwnedConfig<*>)?.file == target }) {
                    "Configuration $path is already registered"
                }
                handles += owned
            }
            return owned
        }

        override fun <T : Any> serializer(type: Class<T>, serializer: ConfigSerializer<T>): ConfigScope = apply {
            check(!scopeClosed.get()) { "Configuration scope is closed" }
            synchronized(serializers) { serializers[type] = serializer }
        }

        override fun <T : Any> publishType(
            baseType: Class<T>, implementation: Class<out T>, name: String,
            aliases: Set<String>, priority: Int, access: ConfigTypeAccess,
        ): ConfigTypeRegistration {
            check(!scopeClosed.get()) { "Configuration scope is closed" }
            val normalizedName = normalizeTypeName(name)
            val normalizedAliases = aliases.map(::normalizeTypeName).toSet()
            require(normalizedName !in normalizedAliases) { "Configuration type aliases must not repeat '$normalizedName'" }
            require(baseType.isAssignableFrom(implementation)) {
                "${implementation.name} does not implement ${baseType.name}"
            }
            val registration = TypeRegistration(
                pluginId, baseType, implementation, normalizedName,
                normalizedAliases, priority, access,
            )
            synchronized(runtimeTypes) {
                check(!scopeClosed.get()) { "Configuration scope is closed" }
                require(runtimeTypes.none {
                    it.isActive && it.owner == pluginId && it.baseType == baseType &&
                        (it.name == normalizedName || normalizedName in it.aliases || it.name in normalizedAliases)
                }) { "Plugin $pluginId already published configuration type '$normalizedName' for ${baseType.name}" }
                runtimeTypes += registration
            }
            return registration
        }

        private fun normalizeTypeName(value: String): String = value.trim().lowercase(Locale.ROOT).also {
            require(it.matches(Regex("[a-z0-9][a-z0-9_.-]{0,63}"))) {
                "Configuration type name '$value' must contain only letters, digits, dots, underscores, or hyphens"
            }
        }

        override fun loadAll() = eachHandle { it.load() }
        override fun reloadAll() = eachHandle { it.reload() }
        override fun saveAll() = eachHandle(onlyLoaded = true) { it.save() }

        override fun close() {
            if (!scopeClosed.compareAndSet(false, true)) return
            val owned = synchronized(handles) { handles.toList().also { handles.clear() } }
            runAll(
                { runAll(owned.map { handle -> { handle.close() } }) },
                {
                    synchronized(runtimeTypes) {
                        val published = runtimeTypes.filter { it.owner == pluginId }
                        published.forEach { it.deactivate() }
                        runtimeTypes.removeAll(published.toSet())
                    }
                },
                {
                    synchronized(scopes) {
                        scopes[owner]?.let { byPlugin ->
                            byPlugin.remove(pluginId, this)
                            if (byPlugin.isEmpty()) scopes.remove(owner)
                        }
                    }
                },
            )
        }

        /** Applies [action] to every handle even when one fails, then rethrows the first failure with the rest suppressed. */
        private fun eachHandle(onlyLoaded: Boolean = false, action: (ManagedConfig<*>) -> Unit) = runAll(
            snapshot().filter { !onlyLoaded || it.isLoaded }.map { handle -> { action(handle) } },
        )

        private fun snapshot() = synchronized(handles) { handles.toList() }
    }

    private fun visibleTypes(consumer: PluginId): List<RuntimeConfigType> = synchronized(runtimeTypes) {
        runtimeTypes.filter { it.isActive && it.access.allows(it.owner, consumer) }.map {
            RuntimeConfigType(it.owner.value, it.baseType, it.implementation, it.name, it.aliases, it.priority)
        }
    }

    private inner class TypeRegistration(
        override val owner: PluginId,
        override val baseType: Class<*>,
        override val implementation: Class<*>,
        override val name: String,
        val aliases: Set<String>,
        val priority: Int,
        val access: ConfigTypeAccess,
    ) : ConfigTypeRegistration {
        @Volatile
        private var active = true
        override val isActive: Boolean get() = active
        override fun close() {
            deactivate()
            synchronized(runtimeTypes) { runtimeTypes.remove(this) }
        }
        fun deactivate() { active = false }
    }

    private class OwnedConfig<T>(val file: Path, private val delegate: ManagedConfig<T>) : ManagedConfig<T> by delegate
}

private fun runAll(vararg steps: () -> Unit) = runAll(steps.toList())

/** Runs every step even when earlier ones fail; the first failure is rethrown with later ones suppressed into it. */
private fun runAll(steps: List<() -> Unit>) {
    var failure: Throwable? = null
    steps.forEach { step ->
        try {
            step()
        } catch (error: Throwable) {
            Failures.rethrowIfFatal(error)
            val first = failure
            if (first == null) failure = error else if (error !== first) first.addSuppressed(error)
        }
    }
    failure?.let { throw it }
}
