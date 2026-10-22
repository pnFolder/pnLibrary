@file:Suppress("DEPRECATION")

package org.pnfolder.pnlibrary.api.actions

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.actions.builtin.DelayAction
import org.pnfolder.pnlibrary.api.audiences.LibraryAudience
import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import org.pnfolder.pnlibrary.api.checks.Check
import org.pnfolder.pnlibrary.api.checks.CheckContext
import org.pnfolder.pnlibrary.api.checks.Requirement
import org.pnfolder.pnlibrary.api.internal.Suggestions
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterState
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import org.pnfolder.pnlibrary.api.text.ComponentSerializerType
import org.pnfolder.pnlibrary.api.text.ComponentService
import java.time.Duration
import java.util.Collections

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
 * @property values named scalar-like values consumed by [Check] implementations
 * @property placeholders plugin-scoped placeholder service used for controlled updates, or `null`
 * @property templates named action lists that `[run]` actions can call
 * @property checks named requirements from the `checks:` block, used by `if` and `require` actions
 * @param initialObjects objects initially available from [get] and [require]
 */
class ActionContext(
    override val player: LibraryPlayer,
    val allPlayers: LibraryAudience,
    val components: ComponentService,
    override val logger: PnLogger,
    val tasks: TaskScope,
    val serializerType: ComponentSerializerType = ComponentSerializerType.ADAPTIVE,
    val values: Map<String, Any?> = emptyMap(),
    initialObjects: Map<Class<*>, Any> = emptyMap(),
    val placeholders: PlaceholderService? = null,
    val templates: Map<String, List<Action>> = emptyMap(),
    val checks: Map<String, Requirement> = emptyMap(),
) : CheckContext {
    private val runtimeValues = LinkedHashMap(values)
    private val objects = HashMap(initialObjects)

    /** Fills in the placeholders of [text] (see [format]), then parses it with [type] or [serializerType]. */
    fun component(text: String, type: ComponentSerializerType? = null): Component =
        components.deserialize(format(text), type ?: serializerType)

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

    /** Whether a `stop` action has ended this invocation; no later action of it runs. */
    var stopped: Boolean = false
        private set

    /** Ends this invocation: actions that have not started yet are skipped. */
    fun stop() {
        stopped = true
    }

    private var templateDepth = 0

    override fun requirement(name: String): Requirement? = checks[name]

    override fun requirementNames(): Set<String> = checks.keys

    /**
     * Executes [actions] in iteration order with this same context, until [stop] is called.
     *
     * A [DelayAction] without actions of its own pauses the actions after it: they run once its duration has
     * passed, scheduled through [tasks]. The caller does not wait for them.
     */
    fun execute(actions: Iterable<Action>) {
        val list = actions as? List<Action> ?: actions.toList()
        for ((index, action) in list.withIndex()) {
            if (stopped) return
            if (action is DelayAction && action.thenActions.isEmpty()) {
                val rest = list.subList(index + 1, list.size)
                if (rest.isNotEmpty()) later(action.duration) { execute(rest) }
                return
            }
            action.execute(this)
        }
    }

    /**
     * Runs the template called [name], or writes a warning when there is none or templates call each other
     * more than [MAX_TEMPLATE_DEPTH] levels deep.
     */
    fun runTemplate(name: String) {
        val list = templates[name]
        if (list == null) {
            val hint = Suggestions.closest(name, templates.keys)
                ?.let { " Did you mean '$it'?" }.orEmpty()
            logger.warning("Template '$name' is not defined.$hint")
            return
        }
        if (templateDepth >= MAX_TEMPLATE_DEPTH) {
            logger.warning("Template '$name' is called too deeply; check that templates do not call each other in a loop")
            return
        }
        templateDepth++
        try {
            execute(list)
        } finally {
            templateDepth--
        }
    }

    private fun later(duration: Duration, block: () -> Unit) {
        tasks.schedule(TaskSpec.builder()
            .execution(TaskExecution.global())
            .delay(duration)
            .action { block() }
            .build())
    }

    /**
     * Reads [expression] as a value: one `[value]`, `{placeholder}` or `%external%`, text that contains them, or a
     * bare name (a context value first, then a pnLibrary placeholder). Returns `null` when nothing matches.
     */
    override fun read(expression: String): Any? {
        val trimmed = expression.trim()
        TOKEN.matchEntire(trimmed)?.let { return valueOf(it) }
        if (TOKEN.containsMatchIn(trimmed)) return format(trimmed)
        return resolve(trimmed)
    }

    /** Like [read], but returns [expression] itself, with its tokens replaced, when nothing matches. */
    override fun textOf(expression: String): String =
        runCatching { read(expression) }.getOrNull()?.toString() ?: format(expression)

    /**
     * Replaces the placeholders in plain [text], each kind in its own brackets, in one pass:
     *
     * | Written | Taken from |
     * |---|---|
     * | `[name]` | a value of this context (and `player`) |
     * | `{name}`, `{plugin:key}` | pnLibrary placeholders: the plugin's own, the library's, another plugin's |
     * | `%name%` | the external PlaceholderAPI only |
     *
     * Any of the first two takes a chain of formats after a bar, such as `{money|group}`.
     * `player` and `player_name` are always the player's name. Text that a placeholder produced is not read again.
     * Unknown names stay as written, so a typo is visible instead of silently empty.
     */
    fun format(text: String): String = TOKEN.replace(text) { match ->
        runCatching { valueOf(match) }.getOrNull()?.toString() ?: match.value
    }

    private fun valueOf(match: MatchResult): Any? {
        val localGroup = match.groups[1]
        val libraryGroup = match.groups[3]
        return when {
            localGroup != null -> named(localGroup.value.trim()) { local(it, match.groupValues[2]) }
            libraryGroup != null -> named(libraryGroup.value.trim()) { library(it) }
            else -> named(requireNotNull(match.groups[4]) { "Token matched no placeholder group" }.value) { external(it) }
        }
    }

    private inline fun named(name: String, lookup: (String) -> Any?): Any? =
        if (name == "player" || name == "player_name") player.name else lookup(name)

    /** A value of this context; a `|format` chain is applied by the placeholder service. */
    private fun local(name: String, pipeline: String): Any? {
        val value = runtimeValues[name] ?: return null
        val service = placeholders
        if (pipeline.isEmpty() || service == null) return value
        return service.render("[$name$pipeline]", player.uniqueId, runtimeValues).toCompletableFuture().join()
    }

    /** A pnLibrary placeholder, looked up without this context's values so the two kinds never mix. */
    private fun library(expression: String): Any? =
        placeholders?.resolve(expression, player.uniqueId)?.toCompletableFuture()?.join()

    /** A PlaceholderAPI placeholder, or `null` when that bridge is not available. */
    private fun external(name: String): Any? {
        val adapter = placeholders?.adapters()?.get(EXTERNAL_ADAPTER) ?: return null
        if (adapter.state != PlaceholderAdapterState.AVAILABLE && adapter.state != PlaceholderAdapterState.REGISTERED) return null
        return adapter.resolve(player.uniqueId, name)
    }

    /** Returns the named condition value, or `null` when it is absent or explicitly null. */
    override fun value(name: String): Any? = runtimeValues[name]

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
        Collections.unmodifiableMap(LinkedHashMap(runtimeValues))

    /** Stores a configuration-facing value for later actions and conditions. */
    fun setValue(name: String, value: Any?): ActionContext = apply {
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
    fun <T : Any> put(type: Class<T>, value: T): ActionContext = apply { objects[type] = value }

    /** Returns the object registered under the exact [type], or `null` when absent. */
    fun <T : Any> get(type: Class<T>): T? = objects[type]?.let(type::cast)

    /** Returns the object registered under [type], failing when the dependency is absent. */
    fun <T : Any> require(type: Class<T>): T = get(type) ?: error("Missing action context value: ${type.name}")

    /** Returns whether an object is registered under the exact [type]. */
    fun has(type: Class<*>): Boolean = objects.containsKey(type)

    /** Removes and returns the object registered under [type], if present. */
    fun <T : Any> remove(type: Class<T>): T? = objects.remove(type)?.let(type::cast)

    companion object {
        /** Deepest chain of templates calling templates before the next call is refused. */
        const val MAX_TEMPLATE_DEPTH = 16

        private const val EXTERNAL_ADAPTER = "placeholderapi"

        /** `[value]`, `{placeholder}` and `%external%`, each with its own capture groups. */
        private val TOKEN = Regex("\\[([^\\[\\]{}|%]+)((?:\\|[^\\[\\]{}]*)?)]|\\{(?![?:/])([^{}]+)}|%([A-Za-z0-9_.:-]+)%")
    }
}
