package ru.privatenull.pnlibrary.api.config

import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.regex.Pattern

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
    /** Returns whether [consumer] may use a type published by [owner]. */
    fun allows(owner: PluginId, consumer: PluginId): Boolean {
        if (consumer in deniedPlugins) return false
        if (owner == consumer) return true
        return allLibraryPlugins || consumer in allowedPlugins || allowedPatterns.any {
            glob(it).matcher(consumer.value).matches()
        }
    }

    /** Ready-made visibility policies and the custom-policy builder entry point. */
    companion object {
        /** Creates an access policy that allows only the publishing plugin. */
        @JvmStatic
        fun ownerOnly() = Builder().build()

        /** Alias for [ownerOnly]. */
        @JvmStatic
        fun local() = ownerOnly()

        /** Creates an access policy that allows every pnLibrary plugin. */
        @JvmStatic
        fun everyone() = Builder().allowAll().build()

        /** Alias for [everyone]. */
        @JvmStatic
        fun global() = everyone()

        /** Creates an owner-plus-explicit-plugins access policy. */
        @JvmStatic fun plugins(vararg pluginIds: String) =
            Builder().allow(*pluginIds).build()

        /** Creates a mutable access-policy builder. */
        @JvmStatic
        fun builder() = Builder()

        private fun glob(value: String): Pattern {
            val expression = value.split('*').joinToString(".*", transform = Pattern::quote)
            return Pattern.compile("^$expression$", Pattern.CASE_INSENSITIVE)
        }
    }

    /** Fluent builder for [ConfigTypeAccess]. */
    class Builder {
        private var all = false
        private val allowed = linkedSetOf<PluginId>()
        private val patterns = linkedSetOf<String>()
        private val denied = linkedSetOf<PluginId>()

        /** Allows every pnLibrary plugin unless denied explicitly. */
        fun allowAll() = apply { all = true }
        /** Allows the supplied normalized plugin identifiers. */
        fun allow(vararg pluginIds: String) = apply { pluginIds.map(PluginId::of).forEach(allowed::add) }
        /** Allows plugin identifiers matching any supplied case-insensitive glob. */
        fun allowMatching(vararg patterns: String) = apply {
            patterns.map(String::trim).filter(String::isNotEmpty).forEach(this.patterns::add)
        }
        /** Denies the supplied plugins, overriding all allow rules. */
        fun deny(vararg pluginIds: String) = apply { pluginIds.map(PluginId::of).forEach(denied::add) }
        /** Creates an immutable access policy. */
        fun build() = ConfigTypeAccess(all, allowed.toSet(), patterns.toSet(), denied.toSet())
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
