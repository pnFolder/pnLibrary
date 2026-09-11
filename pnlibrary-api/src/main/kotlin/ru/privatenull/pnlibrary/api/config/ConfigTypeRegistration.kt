package ru.privatenull.pnlibrary.api.config

import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.regex.Pattern

/** Controls which plugin configurations may use a dynamically published type. */
class ConfigTypeAccess private constructor(
    val allLibraryPlugins: Boolean,
    val allowedPlugins: Set<PluginId>,
    val allowedPatterns: Set<String>,
    val deniedPlugins: Set<PluginId>,
) {
    fun allows(owner: PluginId, consumer: PluginId): Boolean {
        if (consumer in deniedPlugins) return false
        if (owner == consumer) return true
        return allLibraryPlugins || consumer in allowedPlugins || allowedPatterns.any {
            glob(it).matcher(consumer.value).matches()
        }
    }

    companion object {
        @JvmStatic fun ownerOnly() = Builder().build()
        @JvmStatic fun local() = ownerOnly()
        @JvmStatic fun everyone() = Builder().allowAll().build()
        @JvmStatic fun global() = everyone()
        @JvmStatic fun plugins(vararg pluginIds: String) =
            Builder().allow(*pluginIds).build()
        @JvmStatic fun builder() = Builder()

        private fun glob(value: String): Pattern {
            val expression = value.split('*').joinToString(".*", transform = Pattern::quote)
            return Pattern.compile("^$expression$", Pattern.CASE_INSENSITIVE)
        }
    }

    class Builder {
        private var all = false
        private val allowed = linkedSetOf<PluginId>()
        private val patterns = linkedSetOf<String>()
        private val denied = linkedSetOf<PluginId>()

        fun allowAll() = apply { all = true }
        fun allow(vararg pluginIds: String) = apply { pluginIds.map(PluginId::of).forEach(allowed::add) }
        fun allowMatching(vararg patterns: String) = apply {
            patterns.map(String::trim).filter(String::isNotEmpty).forEach(this.patterns::add)
        }
        fun deny(vararg pluginIds: String) = apply { pluginIds.map(PluginId::of).forEach(denied::add) }
        fun build() = ConfigTypeAccess(all, allowed.toSet(), patterns.toSet(), denied.toSet())
    }
}

/** Handle for one plugin-owned polymorphic configuration type. */
interface ConfigTypeRegistration : AutoCloseable {
    val owner: PluginId
    val baseType: Class<*>
    val implementation: Class<*>
    val name: String
    val isActive: Boolean
    override fun close()
}
