package org.pnfolder.pnlibrary.api.currency

import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.common.text.Wildcards
import java.util.Collections

/**
 * Immutable policy controlling which registered plugins may resolve a currency.
 *
 * Explicit denials take precedence over every allow rule. The owner rule is checked
 * next, followed by global access, exact plugin IDs, and case-insensitive patterns in
 * which `*` matches any character sequence.
 */
class CurrencyAccess private constructor(
    private val ownerAllowed: Boolean,
    private val allPlugins: Boolean,
    private val allowed: Set<PluginId>,
    private val patterns: Set<String>,
    private val denied: Set<PluginId>,
) {
    /** Returns whether [consumer] may access a currency owned by [owner]. */
    fun allows(owner: PluginId, consumer: PluginId): Boolean {
        if (consumer in denied) return false
        if (ownerAllowed && owner == consumer) return true
        if (allPlugins || consumer in allowed) return true
        return patterns.any { Wildcards.matches(it, consumer.value) }
    }

    /** Common visibility policies and the custom-policy builder entry point. */
    companion object {
        /** Creates a policy visible only to the owning plugin. */
        @JvmStatic
        fun ownerOnly(): CurrencyAccess = Builder().owner().build()
        /** Creates a policy visible to the owner and every pnLibrary plugin. */
        @JvmStatic
        fun shared(): CurrencyAccess = Builder().owner().allowAll().build()
        /** Creates an initially empty policy builder. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    /** Mutable Java-friendly builder for [CurrencyAccess]. */
    class Builder {
        private var owner = false
        private var all = false
        private val allowed = linkedSetOf<PluginId>()
        private val patterns = linkedSetOf<String>()
        private val denied = linkedSetOf<PluginId>()

        /** Allows the currency owner. */
        fun owner(): Builder = apply { owner = true }
        /** Allows every plugin registered with pnLibrary. */
        fun allowAll(): Builder = apply { all = true }
        /** Adds exact validated plugin IDs to the allow set. */
        fun allow(vararg pluginIds: String): Builder = apply { pluginIds.map(PluginId::of).forEach(allowed::add) }
        /** Adds case-insensitive plugin-ID patterns using `*` as a wildcard. */
        fun allowMatching(vararg patterns: String): Builder = apply {
            require(patterns.none(String::isBlank)) { "Currency access patterns must not be blank" }
            patterns.map(String::trim).forEach(this.patterns::add)
        }
        /** Adds exact plugin IDs to the deny set, which has highest priority. */
        fun deny(vararg pluginIds: String): Builder = apply { pluginIds.map(PluginId::of).forEach(denied::add) }
        /** Creates an immutable policy from the current rules. */
        fun build(): CurrencyAccess = CurrencyAccess(
            owner,
            all,
            Collections.unmodifiableSet(LinkedHashSet(allowed)),
            Collections.unmodifiableSet(LinkedHashSet(patterns)),
            Collections.unmodifiableSet(LinkedHashSet(denied)),
        )
    }
}
