package ru.privatenull.pnlibrary.api.text

import net.kyori.adventure.text.Component

/**
 * Text syntax used to convert between strings and Adventure components.
 *
 * Pass a value to [ComponentService.deserialize] when the source format is known:
 *
 * ```kotlin
 * val title = components.deserialize(
 *     "<gold><bold>Server started</bold></gold>",
 *     ComponentSerializerType.MINI_MESSAGE,
 * )
 * ```
 */
enum class ComponentSerializerType {
    /**
     * Accepts JSON, MiniMessage, and common legacy formatting in one input format.
     *
     * A value beginning with `{` or `[` is parsed as Adventure JSON when it is valid.
     * Every other value is parsed as MiniMessage after converting legacy `&` and `§`
     * codes, six-digit hex colors, and the old gradient form.
     *
     * Examples:
     *
     * ```text
     * &aSuccess                       -> green "Success"
     * &#55ff55Success                 -> #55ff55 "Success"
     * <gradient:red:gold>Hi</gradient> -> red-to-gold MiniMessage gradient
     * {"text":"Hi","color":"gold"} -> gold Adventure JSON component
     * ```
     */
    ADAPTIVE,
    /**
     * Adventure MiniMessage markup.
     *
     * ```text
     * <yellow>Hello <bold>Alex</bold></yellow>
     * <gradient:#ff0000:#ffff00>Warning</gradient>
     * <click:open_url:'https://example.com'>Open website</click>
     * ```
     *
     * Legacy `&a` codes are ordinary text in this mode.
     */
    MINI_MESSAGE,
    /**
     * Legacy Minecraft formatting codes introduced by `&`.
     *
     * ```text
     * &aGreen text &lwith bold text&r and normal text
     * ```
     *
     * MiniMessage tags such as `<green>` are ordinary text in this mode.
     */
    LEGACY_AMPERSAND,
    /**
     * Legacy Minecraft formatting codes introduced by the section sign (`§`).
     *
     * ```text
     * §cRed text §lwith bold text§r and normal text
     * ```
     *
     * This format is mainly useful for native server strings that already contain
     * section-sign codes. Configuration files should usually use [MINI_MESSAGE] or
     * [LEGACY_AMPERSAND].
     */
    LEGACY_SECTION,
    /**
     * Adventure's JSON component representation.
     *
     * ```json
     * {"text":"Welcome ","color":"yellow","extra":[{"text":"Alex","bold":true}]}
     * ```
     *
     * Invalid JSON or an invalid component structure causes deserialization to fail.
     */
    ADVENTURE_JSON,
    /**
     * Literal text with no formatting interpretation.
     *
     * ```text
     * <red>This stays literal</red> and so does &a this code
     * ```
     *
     * Use this for user-generated content that must never activate markup.
     */
    PLAIN_TEXT,
}

/**
 * Bidirectional converter for one [ComponentSerializerType].
 *
 * Implementations must be safe for repeated use. Unless stated by an implementation,
 * malformed input may throw a format-specific runtime exception.
 */
interface ComponentSerializer {
    /** Syntax handled by this serializer. */
    val type: ComponentSerializerType

    /** Parses one [input] string into an Adventure component. */
    fun deserialize(input: String): Component

    /** Deserializes every input independently and preserves list boundaries. */
    fun deserializeAll(inputs: Iterable<String>): List<Component> = inputs.map(::deserialize)

    /** Deserializes lines and joins them with real newline components, without a trailing newline. */
    fun deserializeLines(lines: Iterable<String>): Component = joinComponentLines(deserializeAll(lines))

    /** Vararg convenience overload for [deserializeLines]. */
    fun deserializeLines(vararg lines: String): Component = deserializeLines(lines.asList())

    /** Serializes [component] using this serializer's syntax. */
    fun serialize(component: Component): String

    /** Serializes every component independently and preserves list boundaries. */
    fun serializeAll(components: Iterable<Component>): List<String> = components.map(::serialize)
}

/** Ownership boundary used for parsed-component cache entries. */
enum class ComponentCacheScope {
    /** Bypasses the cache and parses every request. */
    NONE,
    /** Isolates entries by the plugin that owns the component service. */
    PLUGIN,
    /** Allows compatible service instances to share cached entries. */
    GLOBAL,
}

/**
 * Immutable limits for the parsed-component cache.
 *
 * @property scope ownership boundary for cached entries; [ComponentCacheScope.NONE]
 * disables caching
 * @property maximumEntries maximum number of retained entries
 * @property expireAfterAccessMillis idle time after which an entry may be evicted
 * @throws IllegalArgumentException when either numeric limit is not positive
 */
data class ComponentCachePolicy @JvmOverloads constructor(
    val scope: ComponentCacheScope = ComponentCacheScope.PLUGIN,
    val maximumEntries: Int = 1_000,
    val expireAfterAccessMillis: Long = 30 * 60 * 1_000L,
) {
    init {
        require(maximumEntries > 0) { "maximumEntries must be positive" }
        require(expireAfterAccessMillis > 0) { "expireAfterAccessMillis must be positive" }
    }
}

/**
 * Point-in-time component-cache counters.
 *
 * @property hits successful cache lookups
 * @property misses lookups that required parsing
 * @property evictions entries removed by capacity or expiry policy
 * @property size entries retained when the snapshot was created
 */
data class ComponentCacheStatistics(
    val hits: Long, val misses: Long, val evictions: Long, val size: Int,
) {
    /** Ratio of hits to all lookups, or `0.0` before the first lookup. */
    val hitRate: Double get() = if (hits + misses == 0L) 0.0 else hits.toDouble() / (hits + misses)
}

/** Mutable rendering invocation created from one serialized component template. */
interface ComponentTemplate {
    /** Associates a named template token with [value]. */
    fun value(name: String, value: Any?): ComponentTemplate

    /** Selects the player used by player-aware placeholder resolution. */
    fun player(playerId: java.util.UUID): ComponentTemplate

    /** Resolves configured values and placeholders into the final component. */
    fun render(): Component
}

/**
 * Central text conversion, templating, and cache service.
 *
 * Overloads without an explicit serializer use [defaultSerializerType]. Returned
 * components are Adventure values and can be safely further composed by callers.
 */
interface ComponentService {
    /** Serializer selected by overloads that do not accept an explicit type. */
    var defaultSerializerType: ComponentSerializerType

    /** Returns the registered serializer for [type]. */
    fun serializer(type: ComponentSerializerType): ComponentSerializer

    /** Parses [input] with [defaultSerializerType]. */
    fun deserialize(input: String): Component

    /** Parses [input] with the selected serializer [type]. */
    fun deserialize(input: String, type: ComponentSerializerType): Component
    /** Returns one component per source string. */
    fun deserializeAll(inputs: Iterable<String>): List<Component>
    /** Parses each input independently with [type]. */
    fun deserializeAll(inputs: Iterable<String>, type: ComponentSerializerType): List<Component>
    /** Returns one multiline component with `Component.newline()` between source strings. */
    fun deserializeLines(lines: Iterable<String>): Component
    /** Parses and joins [lines] with explicit serializer [type]. */
    fun deserializeLines(lines: Iterable<String>, type: ComponentSerializerType): Component
    /** Vararg convenience overload using [defaultSerializerType]. */
    fun deserializeLines(vararg lines: String): Component = deserializeLines(lines.asList())
    /** Serializes [component] with [defaultSerializerType]. */
    fun serialize(component: Component): String
    /** Serializes each component independently with [defaultSerializerType]. */
    fun serializeAll(components: Iterable<Component>): List<String> = components.map(::serialize)
    /** Creates an isolated rendering invocation for [input]. */
    fun template(input: String): ComponentTemplate
    /** Replaces the active parsed-component cache policy. */
    fun configureCache(policy: ComponentCachePolicy)
    /** Removes all parsed components from the active cache. */
    fun clearCache()
    /** Returns current cache counters and retained size. */
    fun cacheStatistics(): ComponentCacheStatistics
}

private fun joinComponentLines(lines: Iterable<Component>): Component {
    val iterator = lines.iterator()
    if (!iterator.hasNext()) return Component.empty()
    var result = iterator.next()
    while (iterator.hasNext()) {
        result = result.append(Component.newline()).append(iterator.next())
    }
    return result
}
