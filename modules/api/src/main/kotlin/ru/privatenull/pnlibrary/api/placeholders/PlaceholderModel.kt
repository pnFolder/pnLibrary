package ru.privatenull.pnlibrary.api.placeholders

import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.time.Duration
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CompletionStage

/**
 * Validated placeholder identifier and the value type produced by its resolver.
 *
 * @property value lowercase key containing letters, digits, `_`, `.`, `-`, or braces
 * @property type runtime value type used for formatter lookup and Java interop
 */
data class PlaceholderKey<T : Any>(val value: String, val type: Class<T>) {
    init {
        require(value.matches(VALID_KEY)) { "Invalid placeholder key: $value" }
    }

    /** Java-friendly factory and validation rules for placeholder keys. */
    companion object {
        private val VALID_KEY = Regex("[a-z0-9_.{}-]+")

        /** Creates and validates a typed placeholder key. */
        @JvmStatic
        fun <T : Any> of(value: String, type: Class<T>) = PlaceholderKey(value, type)
    }
}

/**
 * Immutable invocation data passed to a placeholder resolver.
 *
 * @property owner plugin that registered the placeholder
 * @property consumer plugin requesting the value
 * @property playerId player context, or `null` for a player-independent request
 * @property parameters named parameters extracted from the placeholder expression
 * @property values additional invocation values supplied by the caller
 */
data class PlaceholderRequest(
    val owner: PluginId,
    val consumer: PluginId,
    val playerId: UUID?,
    val parameters: Map<String, String>,
    val values: Map<String, Any?>,
) {
    /** Returns [playerId] or fails when this invocation has no player context. */
    fun requirePlayerId(): UUID = playerId ?: error("This placeholder requires a player")

    /** Returns a named parameter or fails when it was not supplied. */
    fun parameter(name: String): String = parameters[name] ?: error("Missing placeholder parameter: $name")
}

/** Resolves one placeholder synchronously; `null` selects its configured fallback. */
fun interface PlaceholderResolver<T : Any> {
    /** Produces a value for [request], or `null` when no value is available. */
    fun resolve(request: PlaceholderRequest): T?
}

/** Resolves one placeholder asynchronously; a null result selects its fallback. */
fun interface AsyncPlaceholderResolver<T : Any> {
    /** Starts resolution and returns its eventual value. */
    fun resolve(request: PlaceholderRequest): CompletionStage<T?>
}

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
        return allowedPatterns.any { wildcard(it, consumer.value) }
    }
    /** Common policies and the Java-friendly custom-policy entry point. */
    companion object {
        /** Creates a policy visible only to the plugin that owns the placeholder. */
        @JvmStatic fun ownerOnly() = Builder().owner().build()
        /** Creates a policy visible to the owner and every pnLibrary plugin. */
        @JvmStatic fun shared() = Builder().owner().allowAllLibraryPlugins().build()
        /** Creates an initially empty policy that denies access until rules are added. */
        @JvmStatic fun builder() = Builder()
        private fun wildcard(pattern: String, value: String) = Regex(
            "^" + pattern.split('*').joinToString(".*", transform = Regex::escape) + "$", RegexOption.IGNORE_CASE
        ).matches(value)
    }
    /** Mutable builder for composing an immutable [PlaceholderAccess] policy. */
    class Builder {
        private var owner = false
        private var all = false
        private val allow = linkedSetOf<PluginId>()
        private val patterns = linkedSetOf<String>()
        private val deny = linkedSetOf<PluginId>()

        /** Allows the plugin that owns the placeholder. */
        fun owner() = apply { owner = true }
        /** Allows every plugin registered with pnLibrary. */
        fun allowAllLibraryPlugins() = apply { all = true }
        /** Adds exact plugin IDs to the allow set. */
        fun allow(vararg ids: String) = apply { ids.map(PluginId::of).forEach(allow::add) }
        /** Adds case-insensitive plugin-ID patterns; `*` is the wildcard token. */
        fun allowMatching(vararg values: String) = apply { patterns += values }
        /** Explicitly denies plugin IDs, overriding every allow rule. */
        fun deny(vararg ids: String) = apply { ids.map(PluginId::of).forEach(deny::add) }
        /** Creates an immutable access policy from the configured rules. */
        fun build() = PlaceholderAccess(
            owner,
            all,
            Collections.unmodifiableSet(LinkedHashSet(allow)),
            Collections.unmodifiableSet(LinkedHashSet(patterns)),
            Collections.unmodifiableSet(LinkedHashSet(deny)),
        )
    }
}

/** Values included when constructing a placeholder cache key. */
enum class PlaceholderCacheScope {
    /** Disables result caching. */
    NONE,
    /** Shares one cached result across all invocations. */
    GLOBAL,
    /** Isolates results by the consuming plugin. */
    PLUGIN,
    /** Isolates results by player identity. */
    PLAYER,
    /** Isolates results by parsed placeholder arguments. */
    ARGUMENTS,
}

/**
 * Cache policy for one placeholder registration.
 *
 * @property scope invocation dimension used to isolate entries
 * @property maximumEntries maximum retained entries
 * @property expireAfterWriteMillis lifetime measured from insertion
 * @property cacheNullValues whether absent resolver results are cached
 */
data class PlaceholderCachePolicy @JvmOverloads constructor(
    val scope: PlaceholderCacheScope = PlaceholderCacheScope.NONE,
    val maximumEntries: Int = 1_000,
    val expireAfterWriteMillis: Long = 30_000,
    val cacheNullValues: Boolean = false,
) {
    init {
        require(maximumEntries > 0) { "maximumEntries must be positive" }
        require(expireAfterWriteMillis > 0) { "expireAfterWriteMillis must be positive" }
    }
    /** Factories for the most common cache policies. */
    companion object {
        /** Creates a policy with caching disabled. */
        @JvmStatic fun none() = PlaceholderCachePolicy()
        /**
         * Creates a per-player policy retaining at most 10,000 entries for [duration].
         *
         * @throws IllegalArgumentException when [duration] is zero or negative
         */
        @JvmStatic fun player(duration: Duration) =
            PlaceholderCachePolicy(PlaceholderCacheScope.PLAYER, 10_000, duration.toMillis())
    }
}

/** Current lifecycle state of an external placeholder adapter or publication. */
enum class PlaceholderAdapterState {
    /** Adapter is installed and can accept publications. */
    AVAILABLE,
    /** Requested adapter is not currently installed. */
    UNAVAILABLE,
    /** Placeholder has been published successfully. */
    REGISTERED,
    /** Adapter discovery or publication failed. */
    FAILED,
    /** Registration was permanently closed. */
    CLOSED,
}

/**
 * Features supported by one external adapter.
 *
 * @property players accepts online-player context
 * @property offlinePlayers accepts UUIDs for players that are not online
 * @property parameters preserves arguments from external placeholder expressions
 * @property components can exchange Adventure components without flattening to text
 * @property asynchronous can resolve without blocking the adapter's calling thread
 */
data class PlaceholderAdapterCapabilities(
    val players: Boolean = true,
    val offlinePlayers: Boolean = false,
    val parameters: Boolean = true,
    val components: Boolean = false,
    val asynchronous: Boolean = false,
)

/** Handle tracking one publication in an external placeholder system. */
interface ExternalPlaceholderRegistration : AutoCloseable {
    /** Current publication state, including unavailable and failed attempts. */
    val state: PlaceholderAdapterState
    /** Whether this publication has been permanently closed. */
    val isClosed: Boolean get() = state == PlaceholderAdapterState.CLOSED
}

/**
 * Requested external name for a library placeholder.
 *
 * @property adapterId case-insensitive adapter identifier, such as `placeholderapi`
 * @property namespace external namespace override, or `null` for the adapter default
 * @property name external placeholder name override, or `null` for the library key
 */
data class PlaceholderPublication(
    val adapterId: String,
    val namespace: String? = null,
    val name: String? = null,
)

/** Bridge between pnLibrary placeholders and one external placeholder system. */
interface PlaceholderAdapter {
    /** Stable, case-insensitive adapter identifier. */
    val id: String
    /** Current adapter availability. */
    val state: PlaceholderAdapterState
    /** Feature set used to negotiate publication and resolution. */
    val capabilities: PlaceholderAdapterCapabilities
    /** Resolves a value owned by the external platform, or returns `null` when unknown. */
    fun resolve(playerId: UUID?, expression: String): Any? = null
    /** Publishes [resolver] under the external name described by [publication]. */
    fun publish(owner: PluginId, key: String, resolver: PlaceholderResolver<Any>, publication: PlaceholderPublication): ExternalPlaceholderRegistration
}

/** Runtime registry of available external placeholder adapters. */
interface PlaceholderAdapterRegistry {
    /** Registers [adapter] and returns a handle that removes and closes it. */
    fun register(adapter: PlaceholderAdapter): AutoCloseable
    /** Returns the adapter with case-insensitive [id], or `null`. */
    fun get(id: String): PlaceholderAdapter?
    /** Returns an adapter or fails when it is unavailable. */
    fun require(id: String): PlaceholderAdapter = get(id) ?: error("Placeholder adapter $id is unavailable")
    /** Returns a stable snapshot of adapters ordered by identifier. */
    fun all(): List<PlaceholderAdapter>
}
