package org.pnfolder.pnlibrary.config

import org.pnfolder.pnlibrary.api.config.ConfigAddress
import org.pnfolder.pnlibrary.api.config.ConfigId
import org.pnfolder.pnlibrary.api.config.ConfigInfo
import org.pnfolder.pnlibrary.api.config.ConfigLoadResult
import org.pnfolder.pnlibrary.api.config.ConfigOptions
import org.pnfolder.pnlibrary.api.config.ConfigProblem
import org.pnfolder.pnlibrary.api.config.ConfigRoot
import java.util.function.UnaryOperator
import java.util.function.Consumer
import java.util.concurrent.CopyOnWriteArrayList
import org.pnfolder.pnlibrary.config.yaml.ConfigSettingsEditor
import org.pnfolder.pnlibrary.config.yaml.ConfigObjectIntrospector
import org.pnfolder.pnlibrary.api.config.ReloadRequest
import org.pnfolder.pnlibrary.api.config.ConfigValidationException
import org.pnfolder.pnlibrary.api.config.ConfigSetting
import org.pnfolder.pnlibrary.api.config.ConfigEditResult
import org.pnfolder.pnlibrary.api.config.ConfigEditException
import org.pnfolder.pnlibrary.api.config.ConfigChangeCause
import org.pnfolder.pnlibrary.api.config.ConfigChange
import org.pnfolder.pnlibrary.api.config.ConfigApply
import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.config.ConfigSerializer
import org.pnfolder.pnlibrary.api.config.ConfigSerializerInfo
import org.pnfolder.pnlibrary.api.config.ConfigSerializerRegistration
import org.pnfolder.pnlibrary.api.config.ConfigTypeInfo
import org.pnfolder.pnlibrary.api.config.ConfigTypeRegistration
import org.pnfolder.pnlibrary.api.config.ConfigTypes
import org.pnfolder.pnlibrary.api.config.ConfigValueValidator
import org.pnfolder.pnlibrary.api.config.ManagedConfig
import org.pnfolder.pnlibrary.api.config.MissingFilePolicy
import org.pnfolder.pnlibrary.api.config.UnknownValuePolicy
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginAccess
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.common.lang.Failures
import org.pnfolder.pnlibrary.config.yaml.AnnotatedYamlCodec
import org.pnfolder.pnlibrary.config.yaml.CodeFirstYaml
import org.pnfolder.pnlibrary.config.yaml.DatabasePath
import org.pnfolder.pnlibrary.config.yaml.RuntimeConfigType
import org.pnfolder.pnlibrary.config.yaml.UnknownConfigTypeException
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
import org.pnfolder.pnlibrary.spi.platform.PlatformPnLogger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.IdentityHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

/**
 * Hands out one configuration scope per module, rooted at the plugin's data folder, and closes them with their owner.
 *
 * Every registered file is listed under its [ConfigId], so files can be found by plugin, module or identifier.
 * A polymorphic type published with an address works in the files that address covers.
 */
class ConfigurationServiceImpl(
    private val platform: PlatformOwnership,
    private val logging: PlatformLogging = object : PlatformLogging {},
) : ConfigurationRuntime {
    private val scopes = IdentityHashMap<Any, MutableMap<PluginId, Scope>>()
    private val runtimeTypes = linkedSetOf<TypeRegistration>()
    private val runtimeSerializers = linkedSetOf<SerializerRegistration>()
    private val files = linkedMapOf<ConfigId, OwnedConfig<*>>()
    private val closed = AtomicBoolean(false)

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

    override fun types(baseType: Class<*>): List<ConfigTypeInfo> {
        val declared = baseType.getAnnotation(ConfigTypes::class.java)?.value.orEmpty().map { type ->
            ConfigTypeInfo(baseType, type.type.java, type.name, type.aliases.toSet(), null, type.priority, null)
        }
        val added = synchronized(runtimeTypes) {
            runtimeTypes.filter { it.isActive && it.baseType == baseType }.map {
                ConfigTypeInfo(baseType, it.implementation, it.name, it.aliases, it.owner, it.priority, it.target)
            }
        }
        return declared + added
    }

    override fun serializers(): List<ConfigSerializerInfo> = synchronized(runtimeSerializers) {
        runtimeSerializers.filter { it.isActive }.map { ConfigSerializerInfo(it.type, it.owner, it.priority, it.target) }
    }

    /** The additions that file [consumer] uses: those whose address covers it, its module accepts, and that win by priority. */
    private fun visibleSerializers(consumer: ConfigId, policy: PluginAccess): List<SerializerRegistration> =
        synchronized(runtimeSerializers) {
            runtimeSerializers.filter {
                it.isActive && it.target.matches(consumer) && policy.allows(consumer.plugin, it.owner)
            }.sortedByDescending { it.priority }
        }

    private fun registered(): List<OwnedConfig<*>> = synchronized(files) { files.values.toList() }

    // Platforms without per-plugin folders (Velocity) get the conventional plugins/<id> directory.
    private fun ownerDirectory(owner: Any): Path =
        platform.ownerDataFolder(owner) ?: Paths.get("plugins", platform.ownerDetails(owner)["id"] ?: owner.javaClass.simpleName)

    /** The words visible in file [consumer]: those whose address covers it and whose plugin its module accepts. */
    private fun visibleTypes(consumer: ConfigId, policy: PluginAccess): List<RuntimeConfigType> =
        synchronized(runtimeTypes) {
            val covering = runtimeTypes.filter {
                it.isActive && it.target.matches(consumer) && policy.allows(consumer.plugin, it.owner)
            }
            // The plugin that owns the file keeps its own words: an addition made for everybody cannot shadow them.
            covering.filter { added ->
                added.owner == consumer.plugin || covering.none { own ->
                    own.owner == consumer.plugin && own.baseType == added.baseType && own.words().any { it in added.words() }
                }
            }.map {
                RuntimeConfigType(it.owner.value, it.baseType, it.implementation, it.name, it.aliases, it.priority)
            }
        }

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
        private val handles = linkedSetOf<OwnedConfig<*>>()
        private val announcements = mutableListOf<AutoCloseable>()
        private val serializers = BuiltInConfigSerializers.create()
        private val scopeClosed = AtomicBoolean(false)

        @Volatile
        var extensionPolicy: PluginAccess = PluginAccess.shared()
            private set
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
            val target = if (external) externalTarget(path, relative) else checkedTarget(path, relative, rootDirectory(options.root), options.root)
            val effective = effectiveOptions(options)
            val id = ConfigId(plugin, module, options.root, relative.toString().replace('\\', '/'))
            val defaultValue = defaults.get() ?: error("Configuration defaults cannot be null")
            val codec = AnnotatedYamlCodec(
                type = type,
                defaults = defaults,
                serializers = { serializersFor(id) },
                runtimeTypes = { visibleTypes(id, extensionPolicy) },
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
            val owned = OwnedConfig(id, target, type, effective.readOnly, handle, ConfigSettingsEditor(ConfigObjectIntrospector(effective.naming), type), logger)
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

        /** The folder paths of [root] are counted from. */
        private fun rootDirectory(root: ConfigRoot): Path = when (root) {
            ConfigRoot.PLUGIN -> directory
            ConfigRoot.PLUGINS -> directory.parent
            ConfigRoot.SERVER -> directory.parent?.parent
            ConfigRoot.EXTERNAL -> error("An external path is absolute and has no root folder")
        } ?: error("The ${root.name.lowercase()} folder is not known for $plugin")

        /** Someone else's files get strict handling whatever the options ask: never created, always backed up, unknown keys kept. */
        private fun effectiveOptions(options: ConfigOptions): ConfigOptions =
            if (options.root == ConfigRoot.PLUGIN || options.root == ConfigRoot.EXTERNAL) options else options.copy(
                missingFile = MissingFilePolicy.FAIL,
                backups = true,
                unknownValues = if (options.unknownValues == UnknownValuePolicy.REMOVE) UnknownValuePolicy.PRESERVE else options.unknownValues,
            )

        /** An absolute path chosen by the plugin itself; only the file extension is checked. */
        private fun externalTarget(path: String, relative: Path): Path {
            require(relative.isAbsolute) { "An external configuration path must be absolute: $path" }
            require(path.endsWith(".yml", true) || path.endsWith(".yaml", true)) {
                "Configuration file must use .yml or .yaml"
            }
            return relative
        }

        /** The absolute file for [path]; rejects paths outside the root, links out of it and other file extensions. */
        private fun checkedTarget(path: String, relative: Path, base: Path, root: ConfigRoot): Path {
            val label = if (root == ConfigRoot.PLUGIN) "plugin directory" else "${root.name.lowercase()} folder"
            require(!relative.isAbsolute && !relative.startsWith("..")) {
                "Configuration path must stay inside the $label"
            }
            require(path.endsWith(".yml", true) || path.endsWith(".yaml", true)) {
                "Configuration file must use .yml or .yaml"
            }
            val target = base.resolve(relative).normalize()
            require(target.startsWith(base)) { "Configuration path escapes the $label" }
            if (Files.exists(target) && Files.exists(base)) {
                require(target.toRealPath().startsWith(base.toRealPath())) { "Configuration path leaves the $label through a link" }
            }
            return target
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
            visibleSerializers(id, extensionPolicy).forEach { merged.putIfAbsent(it.type, it.serializer) }
            return merged
        }

        override fun <T : Any> publishSerializer(
            type: Class<T>, serializer: ConfigSerializer<T>, priority: Int, target: ConfigAddress?,
        ): ConfigSerializerRegistration {
            check(!scopeClosed.get()) { "Configuration scope is closed" }
            val where = target ?: ConfigAddress.module(plugin, module)
            val foreign = where.isAll || where.plugin != plugin
            require(!foreign || (!type.isPrimitive && FORBIDDEN_PACKAGES.none { type.name.startsWith(it) })) {
                "A serializer for ${type.name} cannot be added to other plugins' files: it would change how ordinary values are read"
            }
            if (!where.isAll) checkAccepted(where)
            val registration = SerializerRegistration(key, plugin, type, serializer, priority, where)
            synchronized(runtimeSerializers) {
                check(!scopeClosed.get()) { "Configuration scope is closed" }
                runtimeSerializers.firstOrNull {
                    it.isActive && it.owner == plugin && it.type == type && it.target.overlaps(where)
                }?.let {
                    error("Plugin $plugin already published a serializer for ${type.name} that works at ${it.target}")
                }
                runtimeSerializers += registration
            }
            return registration
        }

        override fun extensions(policy: PluginAccess): ConfigScope = apply {
            check(!scopeClosed.get()) { "Configuration scope is closed" }
            extensionPolicy = policy
        }

        override fun list(): List<ConfigInfo> = snapshot().map { it.info }

        override fun get(path: String): ManagedConfig<*>? {
            val root = ConfigRoot.entries.filter { it != ConfigRoot.PLUGIN }.firstOrNull { path.startsWith(it.prefix) } ?: ConfigRoot.PLUGIN
            val relative = path.removePrefix(root.prefix).replace('\\', '/')
            return runCatching { Paths.get(relative).normalize().toString().replace('\\', '/') }.getOrNull()
                ?.let { synchronized(files) { files[ConfigId(plugin, module, root, it)] } }
        }

        override fun <T : Any> publishType(
            baseType: Class<T>, implementation: Class<out T>, name: String,
            aliases: Set<String>, priority: Int, target: ConfigAddress?,
        ): ConfigTypeRegistration {
            check(!scopeClosed.get()) { "Configuration scope is closed" }
            val where = target ?: ConfigAddress.module(plugin, module)
            val normalizedName = normalizeTypeName(name)
            val normalizedAliases = aliases.map(::normalizeTypeName).toSet()
            require(normalizedName !in normalizedAliases) { "Configuration type aliases must not repeat '$normalizedName'" }
            require(baseType.isAssignableFrom(implementation)) {
                "${implementation.name} does not implement ${baseType.name}"
            }
            val words = normalizedAliases + normalizedName
            // The base type's own words are not for sale: an addition must not change what an existing file means.
            baseType.getAnnotation(ConfigTypes::class.java)?.value?.forEach { declared ->
                val taken = (declared.aliases.toList() + declared.name).map { it.lowercase(Locale.ROOT) }
                require(taken.none { it in words }) {
                    "Configuration type word '${taken.first { it in words }}' is declared by ${baseType.simpleName} itself and cannot be taken"
                }
            }
            if (!where.isAll) checkAccepted(where)
            val registration = TypeRegistration(
                key, plugin, baseType, implementation, normalizedName,
                normalizedAliases, priority, where,
            )
            synchronized(runtimeTypes) {
                check(!scopeClosed.get()) { "Configuration scope is closed" }
                runtimeTypes.filter {
                    it.isActive && it.baseType == baseType && it.target.overlaps(where) && it.words().any { word -> word in words }
                }.forEach { other ->
                    require(other.owner != plugin) {
                        "Plugin $plugin already published configuration type '$normalizedName' for ${baseType.name}"
                    }
                    // The plugin that owns the file keeps its own words; an addition can only add new ones.
                    require(where.isAll || other.owner != where.plugin) {
                        "Configuration type word '${other.words().first { word -> word in words }}' of ${baseType.simpleName} belongs to plugin ${where.plugin}"
                    }
                }
                runtimeTypes += registration
            }
            reloadWaiting(where)
            return registration
        }

        /** Refuses the addition when a module it would reach has closed itself to the registering plugin. */
        private fun checkAccepted(where: ConfigAddress) {
            val reached = synchronized(scopes) { scopes.values.flatMap { it.values } }.filter {
                it.plugin == where.plugin && (where.module == null || where.module == it.module)
            }
            reached.forEach {
                check(it.extensionPolicy.allows(it.plugin, plugin)) {
                    "Module ${it.plugin}/${it.module} does not accept configuration types from plugin $plugin"
                }
            }
        }

        private fun normalizeTypeName(value: String): String = value.trim().lowercase(Locale.ROOT).also {
            require(it.matches(Regex("[a-z0-9][a-z0-9_.-]{0,63}"))) {
                "Configuration type name '$value' must contain only letters, digits, dots, underscores, or hyphens"
            }
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
                {
                    synchronized(runtimeTypes) {
                        val published = runtimeTypes.filter { it.scopeKey == key }
                        published.forEach { it.deactivate() }
                        runtimeTypes.removeAll(published.toSet())
                    }
                },
                {
                    synchronized(runtimeSerializers) {
                        val published = runtimeSerializers.filter { it.scopeKey == key }
                        published.forEach { it.deactivate() }
                        runtimeSerializers.removeAll(published.toSet())
                    }
                },
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

    /** A dynamically published polymorphic type; stays visible to codecs until [close] or its scope closes. */
    private inner class TypeRegistration(
        val scopeKey: PluginId,
        override val owner: PluginId,
        override val baseType: Class<*>,
        override val implementation: Class<*>,
        override val name: String,
        val aliases: Set<String>,
        val priority: Int,
        override val target: ConfigAddress,
    ) : ConfigTypeRegistration {
        @Volatile
        private var active = true
        override val isActive: Boolean get() = active
        fun words(): Set<String> = aliases + name
        override fun close() {
            deactivate()
            synchronized(runtimeTypes) { runtimeTypes.remove(this) }
        }
        fun deactivate() { active = false }
    }

    /** A published serializer; used by codecs until [close] or its scope closes. */
    private inner class SerializerRegistration(
        val scopeKey: PluginId,
        override val owner: PluginId,
        override val type: Class<*>,
        val serializer: ConfigSerializer<*>,
        override val priority: Int,
        override val target: ConfigAddress,
    ) : ConfigSerializerRegistration {
        @Volatile
        private var active = true
        override val isActive: Boolean get() = active
        override fun close() {
            deactivate()
            synchronized(runtimeSerializers) { runtimeSerializers.remove(this) }
        }
        fun deactivate() { active = false }
    }

    private companion object {
        val MAIN_MODULE: ModuleId = ModuleId.of("main")
        val FORBIDDEN_PACKAGES = listOf("java.", "javax.", "kotlin.", "jdk.")
    }
}

/**
 * A managed file that remembers its identifier and path. It tells when it was loaded and whether it is waiting for a type,
 * which is the case when its last read failed or skipped list elements because a `type` word was unknown.
 */
private class OwnedConfig<T>(
    override val id: ConfigId,
    val file: Path,
    private val modelType: Class<*>,
    val readOnly: Boolean,
    private val delegate: CodeFirstYaml<T>,
    private val editor: ConfigSettingsEditor,
    private val logger: PnLogger,
) : ManagedConfig<T> by delegate {
    @Volatile
    private var loadedAt: Instant? = null

    /** Whether reading again could bring back what the last read left out. */
    @Volatile
    var waitingForTypes = false
        private set

    /** The strongest requirement of edits that do not work yet; a read clears [ConfigApply.RELOAD], a restart clears the rest. */
    @Volatile
    private var pending: ConfigApply? = null

    private val changeListeners = CopyOnWriteArrayList<Consumer<ConfigChange>>()
    private val reloadListeners = CopyOnWriteArrayList<Consumer<ReloadRequest>>()

    override val info: ConfigInfo
        get() = ConfigInfo(
            id = id,
            modelType = modelType,
            file = file,
            exists = Files.exists(file),
            isLoaded = isLoaded,
            loadedAt = loadedAt,
            readOnly = readOnly,
            skippedUnknownTypes = delegate.skippedUnknownTypes,
            problems = if (isLoaded) runCatching { delegate.validate() }.getOrDefault(emptyList<ConfigProblem>()) else emptyList(),
            pendingApply = pending,
        )

    override val settings: List<ConfigSetting>
        get() = editor.describe(if (isLoaded) delegate.get() else null)

    override fun edit(path: String, text: String): ConfigEditResult {
        check(!readOnly) { "Файл $id открыт только для чтения" }
        val applied = synchronized(delegate) {
            check(isLoaded) { "Файл $id не загружен" }
            val applied = editor.set(delegate.get() as Any, path, text)
            try {
                delegate.save()
            } catch (error: ConfigValidationException) {
                applied.restore()
                throw ConfigEditException(error.problems.joinToString("; ") { "${it.path}: ${it.message}" }, error)
            } catch (error: Exception) {
                applied.restore()
                throw ConfigEditException(error.message ?: "Не удалось записать файл", error)
            }
            applied
        }
        val result = ConfigEditResult(applied.setting, applied.previous)
        if (applied.setting.apply != ConfigApply.LIVE) {
            pending = strongest(pending, applied.setting.apply)
            fire(reloadListeners, ReloadRequest(id, applied.setting.apply, listOf(applied.setting)))
        }
        fire(changeListeners, ConfigChange(id, ConfigChangeCause.EDITED, listOf(path)))
        return result
    }

    override fun onChanged(vararg listeners: Consumer<ConfigChange>): AutoCloseable = subscribe(changeListeners, listeners.toList())

    override fun onReloadRequired(vararg listeners: Consumer<ReloadRequest>): AutoCloseable = subscribe(reloadListeners, listeners.toList())

    override fun load(): ConfigLoadResult<T> = track(ConfigChangeCause.LOADED) { delegate.load() }
    override fun reload(): ConfigLoadResult<T> = track(ConfigChangeCause.RELOADED) { delegate.reload() }
    override fun loadValue(): T = load().value
    override fun reloadValue(): T = reload().value

    override fun save() {
        delegate.save()
        fire(changeListeners, ConfigChange(id, ConfigChangeCause.SAVED, emptyList()))
    }

    override fun save(value: T) {
        delegate.save(value)
        fire(changeListeners, ConfigChange(id, ConfigChangeCause.SAVED, emptyList()))
    }

    override fun update(updater: UnaryOperator<T>): T = delegate.update(updater).also {
        fire(changeListeners, ConfigChange(id, ConfigChangeCause.SAVED, emptyList()))
    }

    override fun resetToDefaults(): T = delegate.resetToDefaults().also {
        fire(changeListeners, ConfigChange(id, ConfigChangeCause.RESET, emptyList()))
    }

    override fun unload() {
        delegate.unload()
        loadedAt = null
    }

    override fun close() = unload()

    private fun track(cause: ConfigChangeCause, action: () -> ConfigLoadResult<T>): ConfigLoadResult<T> = try {
        action().also {
            loadedAt = Instant.now()
            waitingForTypes = delegate.skippedUnknownTypes.isNotEmpty()
            if (pending == ConfigApply.RELOAD) pending = null
            fire(changeListeners, ConfigChange(id, cause, emptyList()))
        }
    } catch (error: Exception) {
        waitingForTypes = generateSequence<Throwable>(error) { it.cause }.any { it is UnknownConfigTypeException }
        throw error
    }

    private fun strongest(a: ConfigApply?, b: ConfigApply): ConfigApply = if (a == null || b.ordinal > a.ordinal) b else a

    private fun <E> subscribe(target: CopyOnWriteArrayList<Consumer<E>>, listeners: List<Consumer<E>>): AutoCloseable {
        target.addAll(listeners)
        return AutoCloseable { target.removeAll(listeners.toSet()) }
    }

    /** A failing listener is logged and never stops the others or the change itself. */
    private fun <E> fire(listeners: List<Consumer<E>>, event: E) {
        listeners.forEach { listener ->
            try {
                listener.accept(event)
            } catch (error: Exception) {
                logger.warning("Обработчик изменения $id завершился ошибкой: ${error.message}", error)
            }
        }
    }
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

/**
 * A file registered through a configuration scope.
 *
 * @property pluginId plugin that registered the file
 * @property directory data folder of the plugin
 * @property path path relative to [directory], with `/` separators
 * @property secretPaths dotted paths of the `@ConfigSecret` fields
 * @property databases sections marked `@BackupDatabase`
 */
class ManagedConfigFile(
    val pluginId: PluginId,
    val directory: Path,
    val path: String,
    val secretPaths: List<String>,
    val databases: List<DatabasePath> = emptyList(),
)
