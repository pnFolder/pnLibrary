package org.pnfolder.pnlibrary.api.config

import org.pnfolder.pnlibrary.api.plugin.PluginId
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
    private var conflict = ConfigTypeConflict.FAIL

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

    /** Chooses what happens when the word is already taken at the same place; see [ConfigTypeConflict]. */
    fun onConflict(policy: ConfigTypeConflict): ConfigTypeBuilder<T> = apply { conflict = policy }

    /** Publishes the type at each of [addresses] and returns one handle per place; a refused place fails the whole call. */
    fun registerEach(addresses: Collection<ConfigAddress>): List<ConfigTypeRegistration> {
        val done = ArrayList<ConfigTypeRegistration>()
        try {
            addresses.forEach { done += into(it).register() }
        } catch (error: RuntimeException) {
            done.forEach(ConfigTypeRegistration::close)
            throw error
        }
        return done
    }

    /** Publishes the type and returns its handle. */
    fun register(): ConfigTypeRegistration {
        val primary = requireNotNull(name) { "Config type name is required: call name(...) before register()" }
        return scope.publishType(baseType, implementation, primary, LinkedHashSet(aliases), priority, target, conflict)
    }
}

/**
 * What to do when a configuration word is already taken at the same place by another registration.
 *
 * ```kotlin
 * scope.type<Action, MyMessage>().name("message-fancy").onConflict(ConfigTypeConflict.KEEP).register()
 * ```
 */
enum class ConfigTypeConflict {
    /** Refuse with an error; the default, so that a clash is never silent. */
    FAIL,

    /** Keep the existing registration and publish nothing; the returned handle is inactive. */
    KEEP,

    /**
     * Take the word over: registrations of the same plugin and ones with a lower priority are closed.
     * A registration of another plugin with the same or a higher priority is never replaced, and the call fails.
     */
    REPLACE,
}
