package org.pnfolder.pnlibrary.api.config

import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.nio.file.Path
import java.time.Instant

/**
 * The folder a configuration path is counted from.
 *
 * A path never leaves its root. Files in [PLUGINS] and [SERVER] belong to somebody else, so they get strict defaults:
 * a missing file is never created, a backup is always made before a rewrite and unknown keys are kept.
 * [EXTERNAL] is the plugin's own choice of an absolute path and has no such limits.
 *
 * @property prefix text put in front of the path in a [ConfigId], empty for [PLUGIN]
 */
enum class ConfigRoot(val prefix: String) {
    /** The data folder of the plugin: `plugins/Name/`. This is the default. */
    PLUGIN(""),

    /** The `plugins/` folder, for example a file of another plugin. */
    PLUGINS("@plugins/"),

    /** The server root, next to `server.properties`. */
    SERVER("@server/"),

    /**
     * Any place on the machine, given by an absolute path: another disk, a mounted or network share, a folder the
     * plugin learned from its own settings. The plugin opens it itself, so it is treated like its own file
     * (it may be created and rewritten). The path in the [ConfigId] is the absolute path with `/` separators.
     */
    EXTERNAL("@external/"),
}

/**
 * Address of one configuration file: plugin, module, root and path.
 *
 * Text form: `plugin/module:path`. A path outside the plugin folder starts with its root:
 * `quests/main:@plugins/Essentials/config.yml`.
 */
data class ConfigId(
    val plugin: PluginId,
    val module: ModuleId,
    val root: ConfigRoot,
    val path: String,
) {
    init {
        require(path.isNotBlank()) { "Configuration path must not be blank" }
        require(!path.startsWith("@")) { "Configuration path must not start with '@'; use the root parameter" }
    }

    /** File name without folders: `daily.yml` for `rewards/daily.yml`. */
    val fileName: String get() = path.substringAfterLast('/')

    /** Folders in front of the file name, empty when the file lies in the root. */
    val directory: String get() = path.substringBeforeLast('/', "")

    /** The address of the module that owns this file. */
    val moduleAddress: ConfigAddress get() = ConfigAddress.module(plugin, module)

    /** The address of this exact file. */
    val address: ConfigAddress get() = ConfigAddress.file(this)

    /** Text form `plugin/module:path`. */
    override fun toString(): String = "${plugin.value}/${module.value}:${root.prefix}$path"

    /** Parsing and building. */
    companion object {
        /** Builds an identifier from plain strings, with the plugin folder as root. */
        @JvmStatic
        @JvmOverloads
        fun of(plugin: String, module: String, path: String, root: ConfigRoot = ConfigRoot.PLUGIN): ConfigId =
            ConfigId(PluginId.of(plugin), ModuleId.of(module), root, path.replace('\\', '/'))

        /** Reads `plugin/module:path`, with an optional `@plugins/` or `@server/` in front of the path. */
        @JvmStatic
        fun parse(text: String): ConfigId {
            val address = ConfigAddress.parse(text)
            require(address.level == ConfigAddress.Level.FILE) { "'$text' is not a file address: expected plugin/module:path" }
            return ConfigId(address.plugin, requireNotNull(address.module), requireNotNull(address.root), requireNotNull(address.path))
        }
    }
}

/**
 * Address of a plugin, a module or a file. The shorter the text, the more files it covers:
 * `quests` covers the whole plugin, `quests/main` one module, `quests/main:quests.yml` one file.
 */
class ConfigAddress private constructor(
    private val owner: PluginId?,
    /** Module, or `null` when the address covers the whole plugin. */
    val module: ModuleId?,
    /** Root of the path, `null` unless the address names a file. */
    val root: ConfigRoot?,
    /** Path inside the root, `null` unless the address names a file. */
    val path: String?,
) {
    /** Plugin of the address. The address of everything ([isAll]) has none and fails here. */
    val plugin: PluginId get() = owner ?: throw IllegalStateException("The address of every file has no plugin")

    /** `true` for [everywhere]: every file of every plugin. */
    val isAll: Boolean get() = owner == null

    /** How much an address covers. */
    enum class Level { ALL, PLUGIN, MODULE, FILE }

    /** Everything, a plugin, a module or a file. */
    val level: Level
        get() = when {
            isAll -> Level.ALL
            module == null -> Level.PLUGIN
            path == null -> Level.MODULE
            else -> Level.FILE
        }

    /** Whether the file [id] lies inside this address. */
    fun matches(id: ConfigId): Boolean =
        isAll || (owner == id.plugin && (module == null || module == id.module) &&
            (path == null || (root == id.root && path == id.path)))

    /** Whether some file can lie inside both this address and [other]. */
    fun overlaps(other: ConfigAddress): Boolean {
        if (isAll || other.isAll) return true
        if (owner != other.owner) return false
        if (module != null && other.module != null && module != other.module) return false
        if (path != null && other.path != null && (root != other.root || path != other.path)) return false
        return true
    }

    /**
     * This plugin's address narrowed to one module: `ConfigAddress.plugin("shop").inModule("main")`.
     * Only an address of a whole plugin can be narrowed.
     */
    fun inModule(module: ModuleId): ConfigAddress {
        require(level == Level.PLUGIN) { "Only the address of a whole plugin can be narrowed to a module, but this one is $this" }
        return ConfigAddress(plugin, module, null, null)
    }

    /** Same as [inModule] with the module written as text. */
    fun inModule(module: String): ConfigAddress = inModule(ModuleId.of(module))

    /**
     * This module's address narrowed to one file: `ConfigAddress.plugin("shop").inModule("main").inFile("rewards.yml")`.
     * [root] says where [path] is counted from, the plugin folder by default.
     */
    @JvmOverloads
    fun inFile(path: String, root: ConfigRoot = ConfigRoot.PLUGIN): ConfigAddress {
        require(level == Level.MODULE) { "Only the address of a module can be narrowed to a file, but this one is $this" }
        val clean = path.replace('\\', '/').removePrefix(root.prefix)
        require(clean.isNotBlank()) { "The path of a file must not be blank" }
        return ConfigAddress(plugin, module, root, clean)
    }

    override fun equals(other: Any?): Boolean = other is ConfigAddress && toString() == other.toString()

    override fun hashCode(): Int = toString().hashCode()

    /** Text form: `*`, `plugin`, `plugin/module` or `plugin/module:path`. */
    override fun toString(): String = if (isAll) "*" else buildString {
        append(plugin.value)
        if (module != null) append('/').append(module.value)
        if (path != null) append(':').append(root?.prefix.orEmpty()).append(path)
    }

    /** Parsing and building. */
    companion object {
        /** Address of every file of every plugin, written `*`. */
        @JvmStatic
        fun everywhere(): ConfigAddress = ConfigAddress(null, null, null, null)

        /** Address of a whole plugin. */
        @JvmStatic
        fun plugin(plugin: PluginId): ConfigAddress = ConfigAddress(plugin, null, null, null)

        /** Address of a whole plugin written as its name. */
        @JvmStatic
        fun plugin(plugin: String): ConfigAddress = plugin(PluginId.of(plugin))

        /** Address of one module written as plugin and module names. */
        @JvmStatic
        fun module(plugin: String, module: String): ConfigAddress = module(PluginId.of(plugin), ModuleId.of(module))

        /** Address of one module. */
        @JvmStatic
        fun module(plugin: PluginId, module: ModuleId): ConfigAddress = ConfigAddress(plugin, module, null, null)

        /** Address of one file. */
        @JvmStatic
        fun file(id: ConfigId): ConfigAddress = ConfigAddress(id.plugin, id.module, id.root, id.path)

        /** Reads `*`, `plugin`, `plugin/module` or `plugin/module:path`. */
        @JvmStatic
        fun parse(text: String): ConfigAddress {
            val value = text.trim()
            require(value.isNotEmpty()) { "Configuration address must not be blank" }
            if (value == "*") return everywhere()
            val colon = value.indexOf(':')
            val head = if (colon < 0) value else value.substring(0, colon)
            val parts = head.split('/')
            require(parts.size <= 2 && parts.all { it.isNotBlank() }) {
                "Invalid configuration address '$text': expected plugin, plugin/module or plugin/module:path"
            }
            val pluginId = PluginId.of(parts[0])
            if (parts.size == 1) {
                require(colon < 0) { "Invalid configuration address '$text': a path needs a module (plugin/module:path)" }
                return ConfigAddress(pluginId, null, null, null)
            }
            val moduleId = ModuleId.of(parts[1])
            if (colon < 0) return ConfigAddress(pluginId, moduleId, null, null)
            var path = value.substring(colon + 1).replace('\\', '/')
            var root = ConfigRoot.PLUGIN
            ConfigRoot.entries.filter { it != ConfigRoot.PLUGIN }.firstOrNull { path.startsWith(it.prefix) }?.let {
                root = it
                path = path.removePrefix(it.prefix)
            }
            require(path.isNotBlank()) { "Invalid configuration address '$text': the path is empty" }
            return ConfigAddress(pluginId, moduleId, root, path)
        }
    }
}

/**
 * Read-only description of one registered configuration file. It never carries values from the file,
 * so `@ConfigSecret` fields cannot leak through it.
 *
 * @property id identifier of the file
 * @property modelType class the file is read into
 * @property file full path on disk
 * @property exists whether the file exists now
 * @property isLoaded whether a value is currently held in memory
 * @property loadedAt time of the last successful load or reload
 * @property readOnly whether the library only reads the file and never writes it
 * @property skippedUnknownTypes list elements skipped at the last load because their `type` is not known
 * @property problems validation problems of the loaded value, empty when not loaded
 * @property pendingApply the strongest requirement of settings changed since the last read that do not work yet, or `null`
 */
data class ConfigInfo(
    val id: ConfigId,
    val modelType: Class<*>,
    val file: Path,
    val exists: Boolean,
    val isLoaded: Boolean,
    val loadedAt: Instant?,
    val readOnly: Boolean,
    val skippedUnknownTypes: List<String>,
    val problems: List<ConfigProblem>,
    val pendingApply: ConfigApply? = null,
) {
    /** The address of this exact file, ready to be passed to `into(...)`. */
    val address: ConfigAddress get() = id.address

    /** The address of the module that owns this file. */
    val moduleAddress: ConfigAddress get() = id.moduleAddress

    /** Plugin of the file. */
    val plugin: PluginId get() = id.plugin

    /** Module of the file. */
    val module: ModuleId get() = id.module

    /** Path inside the root. */
    val path: String get() = id.path

    /** Root of the path. */
    val root: ConfigRoot get() = id.root
}

/**
 * One word that a polymorphic configuration type understands in its `type` field.
 *
 * @property baseType the polymorphic contract
 * @property implementation class created for the word
 * @property name main word
 * @property aliases other accepted words
 * @property owner plugin that added the word, or `null` when the base type declares it itself
 * @property priority wins over a lower priority when two additions use the same word
 * @property target where the word works, or `null` for a word declared by the base type
 */
data class ConfigTypeInfo(
    val baseType: Class<*>,
    val implementation: Class<*>,
    val name: String,
    val aliases: Set<String>,
    val owner: PluginId?,
    val priority: Int,
    val target: ConfigAddress?,
)
