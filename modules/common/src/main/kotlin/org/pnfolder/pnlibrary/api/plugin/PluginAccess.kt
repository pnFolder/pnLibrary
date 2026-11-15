package org.pnfolder.pnlibrary.api.plugin

import org.pnfolder.pnlibrary.common.text.Wildcards
import java.util.Collections

/**
 * Immutable answer to "which plugins may use something that another plugin owns": a placeholder, a currency,
 * the right to add types to a configuration.
 *
 * The rules are checked in this order: a denied plugin never passes; the owner passes if [ownerAllowed];
 * then [allowAll], an exact entry of [allowedPlugins], and the case-insensitive [allowedPatterns]
 * where `*` matches any characters.
 *
 * ```kotlin
 * val access = PluginAccess.builder().owner().allow("scoreboard").allowMatching("admin-*").deny("admin-untrusted").build()
 * PluginAccess.ownerOnly()      // only the owner
 * PluginAccess.shared()         // the owner and every plugin
 * PluginAccess.only("clans")    // the owner and the listed plugins
 * ```
 *
 * @property ownerAllowed whether the plugin that owns the thing may use it
 * @property allowAll whether every plugin may use it unless denied
 * @property allowedPlugins exact plugin IDs that may use it
 * @property allowedPatterns case-insensitive plugin-ID patterns where `*` is a wildcard
 * @property deniedPlugins plugin IDs refused before any allow rule is read
 */
class PluginAccess private constructor(
    val ownerAllowed: Boolean,
    val allowAll: Boolean,
    val allowedPlugins: Set<PluginId>,
    val allowedPatterns: Set<String>,
    val deniedPlugins: Set<PluginId>,
) {
    /** Returns whether [consumer] may use something owned by [owner]. */
    fun allows(owner: PluginId, consumer: PluginId): Boolean {
        if (consumer in deniedPlugins) return false
        if (ownerAllowed && owner == consumer) return true
        if (allowAll || consumer in allowedPlugins) return true
        return allowedPatterns.any { Wildcards.matches(it, consumer.value) }
    }

    /** Ready-made policies and the builder entry point. */
    companion object {
        /** Only the owning plugin. */
        @JvmStatic
        fun ownerOnly(): PluginAccess = Builder().owner().build()

        /** The owner and every plugin. */
        @JvmStatic
        fun shared(): PluginAccess = Builder().owner().allowAll().build()

        /** The owner and the listed plugins. */
        @JvmStatic
        fun only(vararg pluginIds: String): PluginAccess = Builder().owner().allow(*pluginIds).build()

        /** Creates an empty builder: nobody passes until a rule is added. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    /** Fluent builder for [PluginAccess]. */
    class Builder internal constructor() {
        private var owner = false
        private var all = false
        private val allowed = linkedSetOf<PluginId>()
        private val patterns = linkedSetOf<String>()
        private val denied = linkedSetOf<PluginId>()

        /** Lets the owning plugin pass. */
        fun owner(): Builder = apply { owner = true }

        /** Lets every plugin pass unless denied. */
        fun allowAll(): Builder = apply { all = true }

        /** Forgets every allow rule except the owner's own. Denials stay. */
        fun denyAll(): Builder = apply {
            all = false
            allowed.clear()
            patterns.clear()
        }

        /** Lets the plugins with these IDs pass. */
        fun allow(vararg pluginIds: String): Builder = apply { pluginIds.map(PluginId::of).forEach(allowed::add) }

        /** Adds case-insensitive plugin-ID patterns; `*` is the wildcard. */
        fun allowMatching(vararg values: String): Builder = apply {
            require(values.none(String::isBlank)) { "Plugin access patterns must not be blank" }
            values.map(String::trim).forEach(patterns::add)
        }

        /** Refuses the plugins with these IDs, overriding every allow rule. */
        fun deny(vararg pluginIds: String): Builder = apply { pluginIds.map(PluginId::of).forEach(denied::add) }

        /** Creates the immutable policy. */
        fun build(): PluginAccess = PluginAccess(
            owner,
            all,
            Collections.unmodifiableSet(LinkedHashSet(allowed)),
            Collections.unmodifiableSet(LinkedHashSet(patterns)),
            Collections.unmodifiableSet(LinkedHashSet(denied)),
        )
    }
}
