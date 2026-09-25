package ru.privatenull.pnlibrary.api.actions

import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderService
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.text.ComponentSerializerType
import ru.privatenull.pnlibrary.api.text.ComponentService

/**
 * Execution dependencies and per-invocation values shared by an action graph.
 *
 * The context separates portable [Action] implementations from native platform APIs.
 * [player] identifies the subject of the invocation, while [allPlayers] is the wider
 * audience selected by [ActionTarget.ALL_PLAYERS]. Text parsing, logging, and delayed
 * execution remain bound to the plugin that created this context.
 *
 * [values] contains loosely typed values intended for configuration-facing conditions.
 * The map is copied when the context is created and can be changed by value actions
 * while the graph executes.
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
 * @property placeholders plugin-scoped placeholder service used for controlled updates, or `null`
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
    val placeholders: PlaceholderService? = null,
) {
    private val runtimeValues = LinkedHashMap(values)
    private val objects = HashMap(initialObjects)

    /** Parses [text] with [type], or with [serializerType] when no override is supplied. */
    fun component(text: String, type: ComponentSerializerType? = null): Component {
        val normalizedText = LOCAL_PLACEHOLDER.replace(text) { match ->
            "{${match.groupValues[1]}}"
        }
        return components.template(normalizedText, type ?: serializerType)
            .player(player.uniqueId)
            .also { template -> runtimeValues.forEach { (name, value) -> template.value(name, value) } }
            .render()
    }

    /** Parses [lines] as one component using the explicit or context-default serializer. */
    fun component(lines: Iterable<String>, type: ComponentSerializerType? = null): Component {
        val iterator = lines.iterator()
        if (!iterator.hasNext()) return Component.empty()
        var result = component(iterator.next(), type)
        while (iterator.hasNext()) {
            result = result.append(Component.newline()).append(component(iterator.next(), type))
        }
        return result
    }

    /** Resolves an action [target] to the corresponding audience for this invocation. */
    fun target(target: ActionTarget): LibraryAudience = if (target == ActionTarget.PLAYER) player else allPlayers

    /** Executes [actions] in iteration order with this same context. */
    fun execute(actions: Iterable<Action>) = actions.forEach { it.execute(this) }

    /** Returns the named condition value, or `null` when it is absent or explicitly null. */
    fun value(name: String): Any? = runtimeValues[name]

    /** Resolves a local `[name|formatters]` or regular placeholder expression. */
    fun resolve(reference: String): Any? {
        val expression = reference.trim().let {
            if (it.startsWith('[') && it.endsWith(']')) it.substring(1, it.length - 1) else it
        }
        runtimeValues[expression.substringBefore('|').trim()]?.let { return it }
        return placeholders?.resolve(expression, player.uniqueId, runtimeValues)?.toCompletableFuture()?.join()
    }

    /** Returns a snapshot of values currently available to conditions and actions. */
    fun values(): Map<String, Any?> =
        java.util.Collections.unmodifiableMap(LinkedHashMap(runtimeValues))

    /** Stores a configuration-facing value for later actions and conditions. */
    fun setValue(name: String, value: Any?) = apply {
        require(name.isNotBlank()) { "Action context value name must not be blank" }
        runtimeValues[name] = value
    }

    /** Removes a configuration-facing value and returns its previous value. */
    fun removeValue(name: String): Any? = runtimeValues.remove(name)

    /** Updates a writable registered placeholder and returns its resulting value. */
    fun updatePlaceholder(reference: String, value: String): Any? {
        val service = placeholders ?: error("This action context has no placeholder service")
        return service.update(reference, value, player.uniqueId, runtimeValues).toCompletableFuture().join()
    }

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

    private companion object {
        val LOCAL_PLACEHOLDER = Regex("\\[\\[([^\\[\\]]+)]]")
    }
}
