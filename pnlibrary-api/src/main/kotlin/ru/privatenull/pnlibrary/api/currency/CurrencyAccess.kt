package ru.privatenull.pnlibrary.api.currency

import ru.privatenull.pnlibrary.api.plugin.PluginId

/** Controls which registered plugins may resolve a currency. */
class CurrencyAccess private constructor(
    private val ownerAllowed: Boolean,
    private val allPlugins: Boolean,
    private val allowed: Set<PluginId>,
    private val patterns: Set<String>,
    private val denied: Set<PluginId>,
) {
    fun allows(owner: PluginId, consumer: PluginId): Boolean {
        if (consumer in denied) return false
        if (ownerAllowed && owner == consumer) return true
        if (allPlugins || consumer in allowed) return true
        return patterns.any { wildcard(it, consumer.value) }
    }

    companion object {
        @JvmStatic fun ownerOnly() = Builder().owner().build()
        @JvmStatic fun shared() = Builder().owner().allowAll().build()
        @JvmStatic fun builder() = Builder()
        private fun wildcard(pattern: String, value: String) = Regex(
            "^" + pattern.split('*').joinToString(".*", transform = Regex::escape) + "$",
            RegexOption.IGNORE_CASE,
        ).matches(value)
    }

    class Builder {
        private var owner = false
        private var all = false
        private val allowed = linkedSetOf<PluginId>()
        private val patterns = linkedSetOf<String>()
        private val denied = linkedSetOf<PluginId>()

        fun owner() = apply { owner = true }
        fun allowAll() = apply { all = true }
        fun allow(vararg pluginIds: String) = apply { pluginIds.map(PluginId::of).forEach(allowed::add) }
        fun allowMatching(vararg patterns: String) = apply { this.patterns += patterns }
        fun deny(vararg pluginIds: String) = apply { pluginIds.map(PluginId::of).forEach(denied::add) }
        fun build() = CurrencyAccess(owner, all, allowed.toSet(), patterns.toSet(), denied.toSet())
    }
}
