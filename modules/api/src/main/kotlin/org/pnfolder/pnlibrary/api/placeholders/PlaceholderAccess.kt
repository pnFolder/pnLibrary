package org.pnfolder.pnlibrary.api.placeholders

import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.common.text.Wildcards
import java.util.Collections

/**
 * Immutable access policy controlling which plugins may consume a placeholder.
 *
 * Explicit denials have highest priority. Owner access is checked next, followed by
 * global library-plugin access, exact allow entries, and case-insensitive wildcard
 * patterns where `*` matches any sequence of characters.
 *
 * ```java
 * PlaceholderAccess access = PlaceholderAccess.builder()
 *     .owner()
 *     .allow("scoreboard")
 *     .allowMatching("admin-*")
 *     .deny("admin-untrusted")
 *     .build();
 * ```
 *
 * @property ownerAllowed whether the plugin that registered the placeholder is allowed
 * @property allLibraryPlugins whether every plugin registered with pnLibrary is allowed
 * @property allowedPlugins exact plugin IDs explicitly allowed by the policy
 * @property allowedPatterns case-insensitive plugin-ID patterns where `*` is a wildcard
 * @property deniedPlugins exact plugin IDs denied before any allow rule is evaluated
 */
class PlaceholderAccess private constructor(
    val ownerAllowed: Boolean,
    val allLibraryPlugins: Boolean,
    val allowedPlugins: Set<PluginId>,
    val allowedPatterns: Set<String>,
    val deniedPlugins: Set<PluginId>,
) {
    /** Evaluates [consumer] against this policy for a placeholder registered by [owner]. */
    fun allows(owner: PluginId, consumer: PluginId): Boolean {
        if (consumer in deniedPlugins) return false
        if (ownerAllowed && owner == consumer) return true
        if (allLibraryPlugins || consumer in allowedPlugins) return true
        return allowedPatterns.any { Wildcards.matches(it, consumer.value) }
    }
    /** Common policies and the Java-friendly custom-policy entry point. */
    companion object {
        /** Creates a policy visible only to the plugin that owns the placeholder. */
        @JvmStatic
        fun ownerOnly(): PlaceholderAccess = Builder().owner().build()
        /** Creates a policy visible to the owner and every pnLibrary plugin. */
        @JvmStatic
        fun shared(): PlaceholderAccess = Builder().owner().allowAllLibraryPlugins().build()
        /** Creates an initially empty policy that denies access until rules are added. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
    /** Mutable builder for composing an immutable [PlaceholderAccess] policy. */
    class Builder internal constructor() {
        private var owner = false
        private var all = false
        private val allow = linkedSetOf<PluginId>()
        private val patterns = linkedSetOf<String>()
        private val deny = linkedSetOf<PluginId>()

        /** Allows the plugin that owns the placeholder. */
        fun owner(): Builder = apply { owner = true }
        /** Allows every plugin registered with pnLibrary. */
        fun allowAllLibraryPlugins(): Builder = apply { all = true }
        /** Adds exact plugin IDs to the allow set. */
        fun allow(vararg ids: String): Builder = apply { ids.map(PluginId::of).forEach(allow::add) }
        /** Adds case-insensitive plugin-ID patterns; `*` is the wildcard token. */
        fun allowMatching(vararg values: String): Builder = apply {
            require(values.none(String::isBlank)) { "Placeholder access patterns must not be blank" }
            values.map(String::trim).forEach(patterns::add)
        }
        /** Explicitly denies plugin IDs, overriding every allow rule. */
        fun deny(vararg ids: String): Builder = apply { ids.map(PluginId::of).forEach(deny::add) }
        /** Creates an immutable access policy from the configured rules. */
        fun build(): PlaceholderAccess = PlaceholderAccess(
            owner,
            all,
            Collections.unmodifiableSet(LinkedHashSet(allow)),
            Collections.unmodifiableSet(LinkedHashSet(patterns)),
            Collections.unmodifiableSet(LinkedHashSet(deny)),
        )
    }
}
