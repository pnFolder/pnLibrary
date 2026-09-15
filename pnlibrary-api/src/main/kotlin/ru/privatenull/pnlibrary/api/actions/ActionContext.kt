package ru.privatenull.pnlibrary.api.actions

import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.text.ComponentSerializerType
import ru.privatenull.pnlibrary.api.text.ComponentService

/**
 * Immutable execution dependencies and per-invocation values shared by an action graph.
 *
 * The context separates portable [Action] implementations from native platform APIs.
 * [player] identifies the subject of the invocation, while [allPlayers] is the wider
 * audience selected by [ActionTarget.ALL_PLAYERS]. Text parsing, logging, and delayed
 * execution remain bound to the plugin that created this context.
 *
 * [values] contains loosely typed values intended for configuration-facing conditions.
 * [initialObjects] is a type-safe extension map for integrations that need richer
 * runtime objects. The object map is private to this context but remains mutable through
 * [put] and [remove], so callers should not share one context between concurrent action
 * graphs without external synchronization.
 *
 * @property player player that caused or owns this execution
 * @property allPlayers dynamic audience representing all currently eligible players
 * @property components component parser used by text-producing actions
 * @property logger plugin-bound logger used for warnings and diagnostics
 * @property tasks plugin-bound task scope used for deferred work
 * @property serializerType default text serializer for this invocation
 * @property values named scalar-like values consumed by [ActionCondition] implementations
 * @param initialObjects objects initially available from [get] and [require]
 */
class ActionContext(
    val player: LibraryPlayer,
    val allPlayers: LibraryAudience,
    val components: ComponentService,
    val logger: PnLogger,
    val tasks: TaskScope,
    val serializerType: ComponentSerializerType = ComponentSerializerType.ADAPTIVE,
    val values: Map<String, Any?> = emptyMap(),
    initialObjects: Map<Class<*>, Any> = emptyMap(),
) {
    private val objects = HashMap(initialObjects)

    /** Parses [text] with [type], or with [serializerType] when no override is supplied. */
    fun component(text: String, type: ComponentSerializerType? = null): Component = components.deserialize(text, type ?: serializerType)

    /** Parses [lines] as one component using the explicit or context-default serializer. */
    fun component(lines: Iterable<String>, type: ComponentSerializerType? = null): Component = components.deserializeLines(lines, type ?: serializerType)

    /** Resolves an action [target] to the corresponding audience for this invocation. */
    fun target(target: ActionTarget): LibraryAudience = if (target == ActionTarget.PLAYER) player else allPlayers

    /** Executes [actions] in iteration order with this same context. */
    fun execute(actions: Iterable<Action>) = actions.forEach { it.execute(this) }

    /** Returns the named condition value, or `null` when it is absent or explicitly null. */
    fun value(name: String): Any? = values[name]

    /** Associates [value] with [type] and returns this context for fluent setup. */
    fun <T : Any> put(type: Class<T>, value: T) = apply { objects[type] = value }

    /** Returns the object registered under the exact [type], or `null` when absent. */
    fun <T : Any> get(type: Class<T>): T? = objects[type]?.let(type::cast)

    /** Returns the object registered under [type], failing when the dependency is absent. */
    fun <T : Any> require(type: Class<T>): T = get(type) ?: error("Missing action context value: ${type.name}")

    /** Returns whether an object is registered under the exact [type]. */
    fun has(type: Class<*>): Boolean = objects.containsKey(type)

    /** Removes and returns the object registered under [type], if present. */
    fun <T : Any> remove(type: Class<T>): T? = objects.remove(type)?.let(type::cast)
}
