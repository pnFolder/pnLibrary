package org.pnfolder.pnlibrary.api.config

import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.common.text.Wildcards
import java.util.Collections
import java.util.LinkedHashSet

/**
 * Controls which plugin configurations may use a dynamically published polymorphic type.
 *
 * Denials are evaluated first and override every allow rule, including owner access. Otherwise the
 * owner is allowed automatically. Pattern matching is case-insensitive and `*` matches any number
 * of characters; all other pattern characters are treated literally.
 */
class ConfigTypeAccess private constructor(
    /** Whether every pnLibrary plugin is allowed unless explicitly denied. */
    val allLibraryPlugins: Boolean,
    /** Explicitly allowed plugin identifiers. */
    val allowedPlugins: Set<PluginId>,
    /** Case-insensitive glob patterns matched against plugin identifiers. */
    val allowedPatterns: Set<String>,
    /** Explicit denials that override owner and allow rules. */
    val deniedPlugins: Set<PluginId>,
) {
    init {
        require(allowedPatterns.none(String::isBlank)) { "Config type access patterns must not be blank" }
    }

    /** Returns whether [consumer] may use a type published by [owner]. */
    fun allows(owner: PluginId, consumer: PluginId): Boolean {
        if (consumer in deniedPlugins) return false
        if (owner == consumer) return true
        return allLibraryPlugins || consumer in allowedPlugins || allowedPatterns.any {
            Wildcards.matches(it, consumer.value)
        }
    }

    /** Ready-made visibility policies and the custom-policy builder entry point. */
    companion object {
        /** Creates an access policy that allows only the publishing plugin. */
        @JvmStatic
        fun ownerOnly(): ConfigTypeAccess = Builder().build()

        /** Creates an access policy that allows every pnLibrary plugin. */
        @JvmStatic
        fun everyone(): ConfigTypeAccess = Builder().allowAll().build()

        /** Creates an owner-plus-explicit-plugins access policy. */
        @JvmStatic
        fun plugins(vararg pluginIds: String): ConfigTypeAccess =
            Builder().allow(*pluginIds).build()

        /** Creates a mutable access-policy builder. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    /** Fluent builder for [ConfigTypeAccess]. */
    class Builder internal constructor() {
        private var all = false
        private val allowed = linkedSetOf<PluginId>()
        private val patterns = linkedSetOf<String>()
        private val denied = linkedSetOf<PluginId>()

        /** Allows every pnLibrary plugin unless denied explicitly. */
        fun allowAll(): Builder = apply { all = true }
        /** Allows the supplied normalized plugin identifiers. */
        fun allow(vararg pluginIds: String): Builder = apply { pluginIds.map(PluginId::of).forEach(allowed::add) }
        /** Allows plugin identifiers matching any supplied case-insensitive glob. */
        fun allowMatching(vararg patterns: String): Builder = apply {
            require(patterns.none(String::isBlank)) { "Config type access patterns must not be blank" }
            patterns.map(String::trim).forEach(this.patterns::add)
        }
        /** Denies the supplied plugins, overriding all allow rules. */
        fun deny(vararg pluginIds: String): Builder = apply { pluginIds.map(PluginId::of).forEach(denied::add) }
        /** Creates an immutable access policy. */
        fun build(): ConfigTypeAccess = ConfigTypeAccess(
            all,
            Collections.unmodifiableSet(LinkedHashSet(allowed)),
            Collections.unmodifiableSet(LinkedHashSet(patterns)),
            Collections.unmodifiableSet(LinkedHashSet(denied)),
        )
    }
}

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
    /** Whether this registration is still visible to configuration codecs. */
    val isActive: Boolean
    /** Deactivates and removes this registration. Closing is idempotent. */
    override fun close()
}

/**
 * Fluent publication of one polymorphic configuration type, created by [ConfigScope.type].
 *
 * Only [name] is required. Without further calls the type is private to its owner, has priority
 * zero and no aliases. Nothing is published until [register] is called.
 */
class ConfigTypeBuilder<T : Any> internal constructor(
    private val scope: ConfigScope,
    private val baseType: Class<T>,
    private val implementation: Class<out T>,
) {
    private var name: String? = null
    private val aliases = linkedSetOf<String>()
    private var priority = 0
    private var access = ConfigTypeAccess.ownerOnly()

    /** Sets the primary discriminator value. */
    fun name(value: String): ConfigTypeBuilder<T> = apply { name = value }

    /** Adds alternative discriminator values accepted when reading. */
    fun alias(vararg values: String): ConfigTypeBuilder<T> = apply { aliases.addAll(values) }

    /** Sets the priority; the higher value wins when several registrations accept one identifier. */
    fun priority(value: Int): ConfigTypeBuilder<T> = apply { priority = value }

    /** Sets which plugins may use the type. */
    fun access(value: ConfigTypeAccess): ConfigTypeBuilder<T> = apply { access = value }

    /** Sets the access policy through a Java-friendly callback. */
    fun access(configure: java.util.function.Consumer<ConfigTypeAccess.Builder>): ConfigTypeBuilder<T> =
        access(ConfigTypeAccess.builder().also(configure::accept).build())

    /** Publishes the type and returns its handle. */
    fun register(): ConfigTypeRegistration {
        val primary = requireNotNull(name) { "Config type name is required: call name(...) before register()" }
        return scope.publishType(baseType, implementation, primary, LinkedHashSet(aliases), priority, access)
    }
}
