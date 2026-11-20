package org.pnfolder.pnlibrary.api.config

import org.pnfolder.pnlibrary.api.plugin.PluginId

/** Handle for one serializer that a plugin published for the files at some address. */
interface ConfigSerializerRegistration : AutoCloseable {
    /** Plugin that published the serializer. */
    val owner: PluginId

    /** The value type the serializer converts. */
    val type: Class<*>

    /** Where the serializer works: everything, a plugin, a module or one file. */
    val target: ConfigAddress

    /** The higher value wins when several additions handle one type. */
    val priority: Int

    /** Whether the serializer is still used by configuration codecs. */
    val isActive: Boolean

    /** Stops using the serializer. Closing is idempotent. */
    override fun close()
}

/**
 * Fluent publication of one serializer, created by [ConfigScope.serializerFor].
 *
 * Without [into] the serializer works in the files of the module that publishes it. With [into] it works where the
 * address points, for example in another plugin's files, and it lives and dies with the publishing module.
 * Nothing is published until [register] is called.
 */
class ConfigSerializerBuilder<T : Any> internal constructor(
    private val scope: ConfigScope,
    private val type: Class<T>,
    private val serializer: ConfigSerializer<T>,
) {
    private var priority = 0
    private var target: ConfigAddress? = null

    /** Sets the priority; the higher value wins when several additions handle one type. */
    fun priority(value: Int): ConfigSerializerBuilder<T> = apply { priority = value }

    /** Makes the serializer work at [address]: everything, a plugin, a module or one file. */
    fun into(address: ConfigAddress): ConfigSerializerBuilder<T> = apply { target = address }

    /** Short form for the address written as text: `*`, `quests`, `quests/main` or `quests/main:quests.yml`. */
    fun into(address: String): ConfigSerializerBuilder<T> = into(ConfigAddress.parse(address))

    /** Makes the serializer work in one registered file. */
    fun into(config: ManagedConfig<*>): ConfigSerializerBuilder<T> =
        into(requireNotNull(config.id) { "This configuration has no identifier" }.address)

    /** Makes the serializer work in every file of the plugin [plugin]. */
    fun into(plugin: PluginId): ConfigSerializerBuilder<T> = into(ConfigAddress.plugin(plugin))

    /** Makes the serializer work in every file of the module that owns [other], for example another plugin's scope. */
    fun into(other: ConfigScope): ConfigSerializerBuilder<T> = into(ConfigAddress.module(other.plugin, other.module))

    /** Makes the serializer work in the file that [info] describes, for example one found in the catalog. */
    fun into(info: ConfigInfo): ConfigSerializerBuilder<T> = into(info.address)

    /** Makes the serializer work in every file of every plugin. */
    fun everywhere(): ConfigSerializerBuilder<T> = into(ConfigAddress.everywhere())

    /** Publishes the serializer and returns its handle. */
    fun register(): ConfigSerializerRegistration = scope.publishSerializer(type, serializer, priority, target)
}

/**
 * One serializer that plugins added, as the catalog lists it.
 *
 * @property type value type the serializer converts
 * @property owner plugin that published it
 * @property priority wins over a lower priority when two additions handle one type
 * @property target where it works
 */
data class ConfigSerializerInfo(
    val type: Class<*>,
    val owner: PluginId,
    val priority: Int,
    val target: ConfigAddress,
)
