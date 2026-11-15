package org.pnfolder.pnlibrary.api.config

import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.Collections
import java.util.LinkedHashSet

/** Handle for one plugin-owned polymorphic configuration type. */
interface ConfigTypeRegistration : AutoCloseable {
    /** Plugin that published this type. */
    val owner: PluginId
    /** Polymorphic interface or abstract class represented by the discriminator. */
    val baseType: Class<*>
    /** Concrete implementation created for this registration. */
    val implementation: Class<*>
    /** Normalized primary discriminator value. */
    val name: String
    /** Where the word works: a plugin, a module or one file. */
    val target: ConfigAddress
    /** Whether this registration is still visible to configuration codecs. */
    val isActive: Boolean
    /** Deactivates and removes this registration. Closing is idempotent. */
    override fun close()
}

/**
 * Fluent publication of one polymorphic configuration type, created by [ConfigScope.type].
 *
 * Only [name] is required. Without [into] the word works in the configurations of the module that registers it.
 * With [into] it works where the address points, for example in another plugin's file, and it lives and dies
 * with the registering module. Nothing is published until [register] is called.
 */
class ConfigTypeBuilder<T : Any> internal constructor(
    private val scope: ConfigScope,
    private val baseType: Class<T>,
    private val implementation: Class<out T>,
) {
    private var name: String? = null
    private val aliases = linkedSetOf<String>()
    private var priority = 0
    private var target: ConfigAddress? = null

    /** Sets the primary discriminator value. */
    fun name(value: String): ConfigTypeBuilder<T> = apply { name = value }

    /** Adds alternative discriminator values accepted when reading. */
    fun alias(vararg values: String): ConfigTypeBuilder<T> = apply { aliases.addAll(values) }

    /** Sets the priority; the higher value wins when several registrations accept one identifier. */
    fun priority(value: Int): ConfigTypeBuilder<T> = apply { priority = value }

    /** Makes the word work at [address]: a plugin, a module or one file. */
    fun into(address: ConfigAddress): ConfigTypeBuilder<T> = apply { target = address }

    /** Short form for the address written as text: `*`, `quests`, `quests/main` or `quests/main:quests.yml`. */
    fun into(address: String): ConfigTypeBuilder<T> = into(ConfigAddress.parse(address))

    /** Makes the word work in one registered file. */
    fun into(config: ManagedConfig<*>): ConfigTypeBuilder<T> =
        into(requireNotNull(config.id) { "This configuration has no identifier" }.address)

    /** Makes the word work in every file of the plugin [plugin]. */
    fun into(plugin: PluginId): ConfigTypeBuilder<T> = into(ConfigAddress.plugin(plugin))

    /** Makes the word work in every file of the module that owns [other], for example another plugin's scope. */
    fun into(other: ConfigScope): ConfigTypeBuilder<T> = into(ConfigAddress.module(other.plugin, other.module))

    /** Makes the word work in the file that [info] describes, for example one found in the catalog. */
    fun into(info: ConfigInfo): ConfigTypeBuilder<T> = into(info.address)

    /** Makes the word work in every file of every plugin that accepts it. */
    fun everywhere(): ConfigTypeBuilder<T> = into(ConfigAddress.everywhere())

    /** Publishes the type and returns its handle. */
    fun register(): ConfigTypeRegistration {
        val primary = requireNotNull(name) { "Config type name is required: call name(...) before register()" }
        return scope.publishType(baseType, implementation, primary, LinkedHashSet(aliases), priority, target)
    }
}
