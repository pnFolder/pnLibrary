package org.pnfolder.pnlibrary.config

import org.pnfolder.pnlibrary.api.config.ConfigAddress
import org.pnfolder.pnlibrary.api.config.ConfigId
import org.pnfolder.pnlibrary.api.config.ConfigSerializer
import org.pnfolder.pnlibrary.api.config.ConfigSerializerInfo
import org.pnfolder.pnlibrary.api.config.ConfigSerializerRegistration
import org.pnfolder.pnlibrary.api.config.ConfigTypeConflict
import org.pnfolder.pnlibrary.api.config.ConfigTypeInfo
import org.pnfolder.pnlibrary.api.config.ConfigTypeRegistration
import org.pnfolder.pnlibrary.api.config.ConfigTypes
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.config.yaml.RuntimeConfigType
import java.util.Locale

/**
 * The polymorphic types and serializers that plugins publish for the configuration files of others. Each addition
 * belongs to the scope that published it, works in the files its address covers, and goes away with its scope.
 *
 * @param reloadWaiting called with the address of a new type word, so files that waited for it are read again
 */
internal class ConfigAdditions(private val reloadWaiting: (ConfigAddress) -> Unit) {
    private val runtimeTypes = linkedSetOf<TypeRegistration>()
    private val runtimeSerializers = linkedSetOf<SerializerRegistration>()

    fun types(baseType: Class<*>): List<ConfigTypeInfo> {
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

    fun serializers(): List<ConfigSerializerInfo> = synchronized(runtimeSerializers) {
        runtimeSerializers.filter { it.isActive }.map { ConfigSerializerInfo(it.type, it.owner, it.priority, it.target) }
    }

    /** The serializers that file [consumer] uses: those whose address covers it, one per type, the highest priority first. */
    fun visibleSerializers(consumer: ConfigId): Map<Class<*>, ConfigSerializer<*>> = synchronized(runtimeSerializers) {
        val found = LinkedHashMap<Class<*>, ConfigSerializer<*>>()
        runtimeSerializers.filter { it.isActive && it.target.matches(consumer) }
            .sortedByDescending { it.priority }
            .forEach { found.putIfAbsent(it.type, it.serializer) }
        found
    }

    /** The words visible in file [consumer]: those whose address covers it and whose plugin its module accepts. */
    fun visibleTypes(consumer: ConfigId): List<RuntimeConfigType> =
        synchronized(runtimeTypes) {
            val covering = runtimeTypes.filter {
                it.isActive && it.target.matches(consumer)
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

    fun <T : Any> publishSerializer(
        scope: PluginId, plugin: PluginId, module: ModuleId,
        type: Class<T>, serializer: ConfigSerializer<T>, priority: Int, target: ConfigAddress?,
        isOpen: () -> Boolean,
    ): ConfigSerializerRegistration {
        check(isOpen()) { "Configuration scope is closed" }
        val where = target ?: ConfigAddress.module(plugin, module)
        val foreign = where.isAll || where.plugin != plugin
        require(!foreign || (!type.isPrimitive && FORBIDDEN_PACKAGES.none { type.name.startsWith(it) })) {
            "A serializer for ${type.name} cannot be added to other plugins' files: it would change how ordinary values are read"
        }
        val registration = SerializerRegistration(scope, plugin, type, serializer, priority, where)
        synchronized(runtimeSerializers) {
            check(isOpen()) { "Configuration scope is closed" }
            runtimeSerializers.firstOrNull {
                it.isActive && it.owner == plugin && it.type == type && it.target.overlaps(where)
            }?.let {
                error("Plugin $plugin already published a serializer for ${type.name} that works at ${it.target}")
            }
            runtimeSerializers += registration
        }
        return registration
    }

    fun <T : Any> publishType(
        scope: PluginId, plugin: PluginId, module: ModuleId,
        baseType: Class<T>, implementation: Class<out T>, name: String,
        aliases: Set<String>, priority: Int, target: ConfigAddress?,
        conflict: ConfigTypeConflict, isOpen: () -> Boolean,
    ): ConfigTypeRegistration {
        check(isOpen()) { "Configuration scope is closed" }
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
        val registration = TypeRegistration(
            scope, plugin, baseType, implementation, normalizedName,
            normalizedAliases, priority, where,
        )
        synchronized(runtimeTypes) {
            check(isOpen()) { "Configuration scope is closed" }
            val clashing = runtimeTypes.filter {
                it.isActive && it.baseType == baseType && it.target.overlaps(where) && it.words().any { word -> word in words }
            }
            if (clashing.isNotEmpty() && conflict == ConfigTypeConflict.KEEP) {
                registration.deactivate()
                return registration
            }
            clashing.forEach { other ->
                val replaceable = conflict == ConfigTypeConflict.REPLACE && (other.owner == plugin || other.priority < priority)
                require(replaceable || other.owner != plugin) {
                    "Plugin $plugin already published configuration type '$normalizedName' for ${baseType.name}"
                }
                // The plugin that owns the file keeps its own words; an addition can only add new ones.
                require(where.isAll || other.owner != where.plugin || replaceable) {
                    "Configuration type word '${other.words().first { word -> word in words }}' of ${baseType.simpleName} belongs to plugin ${where.plugin}"
                }
            }
            // Another plugin's word with the same or a higher priority stays; priority decides which one wins.
            if (conflict == ConfigTypeConflict.REPLACE) {
                clashing.filter { it.owner == plugin || it.priority < priority }.forEach { it.close() }
            }
            runtimeTypes += registration
        }
        reloadWaiting(where)
        return registration
    }

    private fun normalizeTypeName(value: String): String = value.trim().lowercase(Locale.ROOT).also {
        require(it.matches(Regex("[a-z0-9][a-z0-9_.-]{0,63}"))) {
            "Configuration type name '$value' must contain only letters, digits, dots, underscores, or hyphens"
        }
    }

    /** Takes back everything published through the scope [scope]. */
    fun release(scope: PluginId) {
        synchronized(runtimeTypes) {
            val published = runtimeTypes.filter { it.scopeKey == scope }
            published.forEach { it.deactivate() }
            runtimeTypes.removeAll(published.toSet())
        }
        synchronized(runtimeSerializers) {
            val published = runtimeSerializers.filter { it.scopeKey == scope }
            published.forEach { it.deactivate() }
            runtimeSerializers.removeAll(published.toSet())
        }
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
        val FORBIDDEN_PACKAGES = listOf("java.", "javax.", "kotlin.", "jdk.")
    }
}
