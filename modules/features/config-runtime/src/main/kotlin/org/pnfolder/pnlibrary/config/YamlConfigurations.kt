package org.pnfolder.pnlibrary.config

import org.pnfolder.pnlibrary.api.config.ConfigAddress
import org.pnfolder.pnlibrary.api.config.ConfigId
import org.pnfolder.pnlibrary.api.config.ConfigInfo
import org.pnfolder.pnlibrary.api.config.ConfigOptions
import org.pnfolder.pnlibrary.api.config.ConfigRoot
import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.config.ConfigSerializer
import org.pnfolder.pnlibrary.api.config.ConfigSerializerInfo
import org.pnfolder.pnlibrary.api.config.ConfigSerializerRegistration
import org.pnfolder.pnlibrary.api.config.ConfigTypeConflict
import org.pnfolder.pnlibrary.api.config.ConfigTypeInfo
import org.pnfolder.pnlibrary.api.config.ConfigTypeRegistration
import org.pnfolder.pnlibrary.api.config.ConfigValueValidator
import org.pnfolder.pnlibrary.api.config.ManagedConfig
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.config.yaml.AnnotatedYamlCodec
import org.pnfolder.pnlibrary.config.yaml.CodeFirstYaml
import org.pnfolder.pnlibrary.config.yaml.ConfigObjectIntrospector
import org.pnfolder.pnlibrary.config.yaml.ConfigSettingsEditor
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
import org.pnfolder.pnlibrary.spi.platform.PlatformPnLogger
import java.nio.file.Path
import java.nio.file.Paths
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

/**
 * Hands out one configuration scope per module, rooted at the plugin's data folder, and closes them with their owner.
 *
 * Every registered file is listed under its [ConfigId], so files can be found by plugin, module or identifier.
 * A polymorphic type published with an address works in the files that address covers.
 */
class YamlConfigurations(
    private val platform: PlatformOwnership,
    private val logging: PlatformLogging = object : PlatformLogging {},
) : ConfigurationRuntime {
    private val scopes = IdentityHashMap<Any, MutableMap<PluginId, Scope>>()
    private val files = linkedMapOf<ConfigId, OwnedConfig<*>>()
    private val closed = AtomicBoolean(false)
    private val additions = ConfigAdditions(::reloadWaiting)

    /**
     * Told about every registered file, so that diagnostics can read it and hide its `@ConfigSecret` fields. The
     * returned handle is closed together with the scope.
     */
    @Volatile
    var fileListener: ((ManagedConfigFile) -> AutoCloseable?)? = null

    /** Returns the scope of [owner], using the plugin id the platform reports for it. */
    override fun scope(owner: Any): ConfigScope {
        val pluginId = PluginId.of(platform.ownerDetails(owner)["id"] ?: owner.javaClass.simpleName)
        return scope(owner, pluginId)
    }

    /** Returns the scope of [owner] and [pluginId] as module `main`, creating it on first use. Fails after [close]. */
    override fun scope(owner: Any, pluginId: PluginId): ConfigScope = scope(owner, pluginId, pluginId, MAIN_MODULE)

    /**
     * Returns the scope of [owner] kept under [key] for module [module] of plugin [plugin], creating it on first use.
     * Fails after [close].
     */
    override fun scope(owner: Any, key: PluginId, plugin: PluginId, module: ModuleId): ConfigScope = synchronized(scopes) {
        check(!closed.get()) { "Configuration service is closed" }
        scopes.getOrPut(owner) { linkedMapOf() }.getOrPut(key) {
            Scope(
                owner = owner,
                key = key,
                plugin = plugin,
                module = module,
                directory = ownerDirectory(owner).toAbsolutePath().normalize(),
                logger = PlatformPnLogger(logging, owner, key.value),
            )
        }
    }

    /** Closes the scopes created for [owner] and forgets them. */
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

    // ── Catalog ───────────────────────────────────────────────────────────────────────────────

    override fun all(): List<ConfigInfo> = registered().map { it.info }

    override fun find(address: ConfigAddress): List<ConfigInfo> =
        registered().filter { address.matches(it.id) }.map { it.info }

    override fun get(id: ConfigId): ManagedConfig<*>? = synchronized(files) { files[id] }

    override fun types(baseType: Class<*>): List<ConfigTypeInfo> = additions.types(baseType)

    override fun serializers(): List<ConfigSerializerInfo> = additions.serializers()

    private fun registered(): List<OwnedConfig<*>> = synchronized(files) { files.values.toList() }

    // Platforms without per-plugin folders (Velocity) get the conventional plugins/<id> directory.
    private fun ownerDirectory(owner: Any): Path =
        platform.ownerDataFolder(owner) ?: Paths.get("plugins", platform.ownerDetails(owner)["id"] ?: owner.javaClass.simpleName)

    /** A new word may unblock files that failed or skipped elements for want of it: read those again. */
    private fun reloadWaiting(where: ConfigAddress) {
        val waiting = synchronized(files) { files.values.filter { it.waitingForTypes && where.matches(it.id) } }
        // CodeFirstYaml has already logged why a read failed; a file that still fails keeps waiting.
        waiting.forEach { handle -> runCatching { handle.reload() } }
    }

    /** One module's view of the service: its files, serializers and published types, all released on [close]. */
    private inner class Scope(
        private val owner: Any,
        private val key: PluginId,
        override val plugin: PluginId,
        override val module: ModuleId,
        private val directory: Path,
        private val logger: PnLogger,
    ) : ConfigScope {
        private val paths = ConfigPaths(directory, plugin)
        private val handles = linkedSetOf<OwnedConfig<*>>()
        private val announcements = mutableListOf<AutoCloseable>()
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
            val external = options.root == ConfigRoot.EXTERNAL
            val target = if (external) paths.external(path, relative) else paths.checked(path, relative, options.root)
            val effective = paths.effective(options)
            val id = ConfigId(plugin, module, options.root, relative.toString().replace('\\', '/'))
            val defaultValue = defaults.get() ?: error("Configuration defaults cannot be null")
            val codec = AnnotatedYamlCodec(
                type = type,
                defaults = defaults,
                serializers = { serializersFor(id) },
                runtimeTypes = { additions.visibleTypes(id) },
                consumer = plugin.value,
                options = effective,
                warning = { message -> logger.warning(message) },
            )
            val handle = CodeFirstYaml(
                file = target.toFile(),
                defaults = defaultValue,
                codec = codec,
                logger = logger,
                validator = ConfigValueValidator { value -> codec.validate(value) + validator.validate(value) },
                options = effective,
            )
            val editor = ConfigSettingsEditor(ConfigObjectIntrospector(effective.naming), type)
            val owned = OwnedConfig(id, target, type, effective.readOnly, handle, editor, logger)
            register(owned)
            // Files of other plugins can hold secrets that the diagnostic report must not see, so only the plugin's own are announced.
            if (options.root == ConfigRoot.PLUGIN) {
                announce(ManagedConfigFile(key, directory, id.path, codec.secretPaths, codec.databases))
            } else if (external) {
                // The plugin's own file at a place of its choice: it is still the plugin's secrets, so they are hidden in reports too.
                announce(ManagedConfigFile(key, target.parent, target.fileName.toString(), codec.secretPaths, codec.databases))
            }
            return owned
        }

        /** Adds [owned] to the library-wide list and to this scope; a second writer of one file is refused. */
        private fun register(owned: OwnedConfig<*>) {
            synchronized(files) {
                check(owned.id !in files) { "Configuration ${owned.id} is already registered" }
                if (!owned.readOnly) {
                    files.values.firstOrNull { !it.readOnly && it.file == owned.file }?.let {
                        error("File ${owned.file} is already managed by ${it.id}; register it read-only to only read it")
                    }
                }
                files[owned.id] = owned
            }
            try {
                synchronized(handles) {
                    check(!scopeClosed.get()) { "Configuration scope is closed" }
                    handles += owned
                }
            } catch (error: Throwable) {
                synchronized(files) { files.remove(owned.id) }
                throw error
            }
        }

        // Announcing feeds diagnostics only, so a failing listener is logged and the file still works.
        private fun announce(file: ManagedConfigFile) {
            val announced = try {
                fileListener?.invoke(file) ?: return
            } catch (error: Exception) {
                logger.warning("Диагностика не получила файл ${file.path}: ${error.message}", error)
                return
            }
            val keep = synchronized(handles) { !scopeClosed.get() && announcements.add(announced) }
            if (!keep) announced.close()
        }

        override fun <T : Any> serializer(type: Class<T>, serializer: ConfigSerializer<T>): ConfigScope = apply {
            check(!scopeClosed.get()) { "Configuration scope is closed" }
            synchronized(serializers) { serializers[type] = serializer }
        }

        /** What file [id] reads with: the module's own serializers first, then the additions that cover it. */
        private fun serializersFor(id: ConfigId): Map<Class<*>, ConfigSerializer<*>> {
            val merged = synchronized(serializers) { LinkedHashMap(serializers) }
            additions.visibleSerializers(id).forEach { (type, serializer) -> merged.putIfAbsent(type, serializer) }
            return merged
        }

        override fun <T : Any> publishSerializer(
            type: Class<T>, serializer: ConfigSerializer<T>, priority: Int, target: ConfigAddress?,
        ): ConfigSerializerRegistration =
            additions.publishSerializer(key, plugin, module, type, serializer, priority, target) { !scopeClosed.get() }

        override fun <T : Any> publishType(
            baseType: Class<T>, implementation: Class<out T>, name: String,
            aliases: Set<String>, priority: Int, target: ConfigAddress?,
            conflict: ConfigTypeConflict,
        ): ConfigTypeRegistration =
            additions.publishType(key, plugin, module, baseType, implementation, name, aliases, priority, target, conflict) { !scopeClosed.get() }

        override fun list(): List<ConfigInfo> = snapshot().map { it.info }

        override fun get(path: String): ManagedConfig<*>? {
            val root = ConfigRoot.entries.filter { it != ConfigRoot.PLUGIN }.firstOrNull { path.startsWith(it.prefix) } ?: ConfigRoot.PLUGIN
            val relative = path.removePrefix(root.prefix).replace('\\', '/')
            return runCatching { Paths.get(relative).normalize().toString().replace('\\', '/') }.getOrNull()
                ?.let { synchronized(files) { files[ConfigId(plugin, module, root, it)] } }
        }

        override fun loadAll() = forEach(snapshot()) { it.load() }
        override fun reloadAll() = forEach(snapshot()) { it.reload() }
        override fun saveAll() = forEach(snapshot().filter { it.isLoaded && !it.readOnly }) { it.save() }

        override fun close() {
            if (!scopeClosed.compareAndSet(false, true)) return
            val owned = synchronized(handles) { handles.toList().also { handles.clear() } }
            runAll(
                { runAll(owned.map { handle -> { handle.close() } }) },
                { synchronized(files) { owned.forEach { files.remove(it.id, it) } } },
                { runAll(synchronized(handles) { announcements.toList().also { announcements.clear() } }.map { it -> { it.close() } }) },
                { additions.release(key) },
                {
                    synchronized(scopes) {
                        scopes[owner]?.let { byPlugin ->
                            byPlugin.remove(key, this)
                            if (byPlugin.isEmpty()) scopes.remove(owner)
                        }
                    }
                },
            )
        }

        private fun forEach(handles: List<ManagedConfig<*>>, action: (ManagedConfig<*>) -> Unit) =
            runAll(handles.map { handle -> { action(handle) } })

        private fun snapshot() = synchronized(handles) { handles.toList() }
    }

    private companion object {
        val MAIN_MODULE: ModuleId = ModuleId.of("main")
    }
}
