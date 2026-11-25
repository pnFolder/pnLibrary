@file:Suppress("DEPRECATION")

package org.pnfolder.pnlibrary.api.scenario.actions

import net.kyori.adventure.audience.Audience
import org.pnfolder.pnlibrary.api.audience.name
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer
import org.pnfolder.pnlibrary.api.scenario.expression.Expression
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionException
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionFunction
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec
import org.pnfolder.pnlibrary.api.text.ComponentService
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.time.Duration
import java.util.Collections

/**
 * One run of a scenario: who it is for, the values it knows, the services it may use, and what to undo.
 *
 * Create it with [ActionService.context], run a list with [execute], and read the [ActionResult].
 * A context belongs to one run and is not thread-safe: do not share it between concurrent runs.
 *
 * ```kotlin
 * val context = module.actions.context(player, values = mapOf("item" to "diamond"))
 * when (val result = context.execute(actions)) {
 *     ActionResult.Done -> Unit
 *     is ActionResult.Stopped -> Unit
 *     is ActionResult.Failed -> player.sendMessage(Component.text(result.message))
 * }
 * ```
 *
 * Every text an action prints may mention values, in three kinds of marks, see [format].
 *
 * @property player player the run is for
 * @property allPlayers wider audience for actions aimed at [ActionTarget.ALL_PLAYERS]
 * @property components parser used by actions that print text
 * @property logger plugin logger for warnings
 * @property tasks task scope for delayed work
 * @property serializerType default text format of printed text
 * @param values named values texts and requirements read as `{name}`
 * @param services services handed over directly, by exact type
 * @param serviceLookup fallback for services registered elsewhere, such as in the module's service scope
 * @param lookup actions, requirements and functions plugins defined by name
 * @property placeholders placeholder service, or `null` when the runtime has none
 */
class ActionContext(
    override val player: OnlinePlayer,
    val allPlayers: Audience,
    val components: ComponentService,
    override val logger: PnLogger,
    val tasks: TaskScope,
    val serializerType: ComponentSerializerType = ComponentSerializerType.ADAPTIVE,
    values: Map<String, Any?> = emptyMap(),
    private val services: Map<Class<*>, Any> = emptyMap(),
    private val serviceLookup: (Class<*>) -> Any? = { null },
    private val lookup: ScenarioLookup = ScenarioLookup.NONE,
    val placeholders: PlaceholderService? = null,
) : RequirementContext {
    private val runtimeValues = LinkedHashMap(values)
    private val undoSteps = ArrayList<Pair<String, () -> Unit>>()

    /**
     * Runs [actions] as one scenario: in order, until one stops or fails.
     *
     * A failure undoes the steps that registered an [onUndo] and writes a warning; a thrown exception counts
     * as a failure with the code `action.error`. Use this for the outermost list; actions that contain
     * lists use [runList], so that the undo covers the whole scenario.
     */
    fun execute(actions: List<Action>): ActionResult {
        val result = runList(actions)
        if (result is ActionResult.Failed) {
            logger.warning("Scenario failed (${result.code}): ${result.message}", result.cause)
            rollbackTo(0)
        }
        return result
    }

    /**
     * Runs [actions] in order and returns the first result that is not [ActionResult.Done], without undoing.
     * It is the building block for actions that hold other actions.
     */
    fun runList(actions: List<Action>): ActionResult {
        for (action in actions) {
            val result = try {
                action.run(this)
            } catch (error: Exception) {
                ActionResult.Failed("action.error", error.message ?: error.javaClass.simpleName, error)
            }
            if (result !is ActionResult.Done) return result
        }
        return ActionResult.Done
    }

    /** Runs [actions] as a separate scenario after [delay], without blocking the caller. */
    fun runLater(delay: Duration, actions: List<Action>) {
        if (actions.isEmpty()) return
        tasks.schedule(TaskSpec.builder()
            .execution(TaskExecution.global())
            .delay(delay)
            .action { execute(actions) }
            .build())
    }

    /**
     * Registers how to take back what the current step did, for example giving a charge back.
     *
     * When a later step fails, registered undo steps run in reverse order. [description] is for the log.
     */
    fun onUndo(description: String, undo: () -> Unit) {
        undoSteps += description to undo
    }

    /** A bookmark of the undo list, for [rollbackTo]. */
    fun undoMark(): Int = undoSteps.size

    /** Forgets the undo steps registered after [mark] without running them: what they did stays. */
    fun discardUndo(mark: Int) {
        while (undoSteps.size > mark) undoSteps.removeAt(undoSteps.lastIndex)
    }

    /** Runs and removes the undo steps registered after [mark], newest first. One that throws is logged. */
    fun rollbackTo(mark: Int) {
        while (undoSteps.size > mark) {
            val (description, undo) = undoSteps.removeAt(undoSteps.lastIndex)
            runCatchingNonFatal(undo).onFailure { logger.error("Could not undo: $description", it) }
        }
    }

    override fun <T : Any> service(type: Class<T>): T? = (services[type] ?: serviceLookup(type))?.let(type::cast)

    /** Returns the service of [type], or throws when this run has none. */
    fun <T : Any> requireService(type: Class<T>): T =
        service(type) ?: error("${type.simpleName} is not available to this scenario")

    override fun value(name: String): Any? = runtimeValues[name]

    override fun requirement(name: String): Requirement? = lookup.requirement(name)

    override fun function(name: String): ExpressionFunction? = lookup.function(name)

    /** The action defined under [name] with [ActionService.define], or `null`. */
    fun action(name: String): Action? = lookup.action(name)

    /**
     * Runs [block] with [extra] values added, then puts the values back as they were.
     * Use it to give a called action its arguments without leaking them into the rest of the scenario.
     */
    fun <T> scoped(extra: Map<String, Any?>, block: () -> T): T {
        val saved = extra.keys.associateWith { runtimeValues[it] }
        val had = extra.keys.filter { it in runtimeValues }
        runtimeValues.putAll(extra)
        try {
            return block()
        } finally {
            extra.keys.forEach { if (it in had) runtimeValues[it] = saved[it] else runtimeValues.remove(it) }
        }
    }

    /** Makes [value] available as `[name]` to later steps. */
    fun setValue(name: String, value: Any?): ActionContext = apply {
        require(name.isNotBlank()) { "Value name must not be blank" }
        runtimeValues[name] = value
    }

    /** A snapshot of the values known now. */
    fun values(): Map<String, Any?> = Collections.unmodifiableMap(LinkedHashMap(runtimeValues))

    /** The audience an action aimed at [target] sends to. */
    fun target(target: ActionTarget): Audience = if (target == ActionTarget.PLAYER) player else allPlayers

    /** Fills in the placeholders of [text] (see [format]), then parses it with [type] or [serializerType]. */
    fun component(text: String, type: ComponentSerializerType? = null): Component =
        components.deserialize(format(text), type ?: serializerType)

    /** Parses [lines] as one component, one line under another. */
    fun component(lines: Iterable<String>, type: ComponentSerializerType? = null): Component =
        lines.map { component(it, type) }.reduceOrNull { all, line -> all.append(Component.newline()).append(line) }
            ?: Component.empty()

    /**
     * Replaces the placeholders in plain [text], each kind in its own brackets, in one pass:
     *
     * | Written | Taken from |
     * |---|---|
     * | `{name}` | a value of this run (and `player`), else a pnLibrary placeholder |
     * | `{name}`, `{plugin:key}` | pnLibrary placeholders: the plugin's own, the library's, another plugin's |
     * | `%name%` | the external PlaceholderAPI only |
     *
     * `{name}` takes a chain of formats after a bar, such as `{money|group}`.
     * `player` and `player_name` are always the player's name. Text that a placeholder produced is not read again.
     * Unknown names stay as written, so a typo is visible instead of silently empty.
     */
    fun format(text: String): String = TOKEN.replace(text) { match ->
        runCatchingNonFatal { valueOf(match) }
            .onFailure { logger.error("Placeholder ${match.value} failed; it is left as written", it) }
            .getOrNull()?.let { Expression.format(it) } ?: match.value
    }

    override fun text(expression: String): String =
        runCatchingNonFatal { read(expression) }
            .onFailure { logger.error("Placeholder in '$expression' failed; the text is left as written", it) }
            .getOrNull()?.toString() ?: format(expression)

    override fun read(expression: String): Any? {
        val trimmed = expression.trim()
        TOKEN.matchEntire(trimmed)?.let { return valueOf(it) }
        if (TOKEN.containsMatchIn(trimmed)) return format(trimmed)
        return resolve(trimmed)
    }

    /** Resolves a bare `name` or `{name|formats}` as a run value first, then as a pnLibrary placeholder. */
    fun resolve(reference: String): Any? {
        val expression = reference.trim().let {
            if (it.startsWith("{") && it.endsWith("}")) it.substring(1, it.length - 1) else it
        }
        // Text with tags such as `[<gray>pnTest</gray>]` is a message, not a name: asking placeholders for it only logs a warning.
        if ('<' in expression || '>' in expression) return null
        runtimeValues[expression.substringBefore('|').trim()]?.let { return it }
        return placeholders?.resolve(expression, player.uniqueId, runtimeValues)?.toCompletableFuture()?.join()
    }

    private fun valueOf(match: MatchResult): Any? {
        val braced = match.groups[1]?.value
        if (braced != null && braced.startsWith("=")) return calculate(braced.substring(1))
        return when {
            braced != null -> named(braced.substringBefore('|').trim()) { braced(braced.trim()) }
            else -> named(requireNotNull(match.groups[2]) { "Token matched no placeholder group" }.value) { external(it) }
        }
    }

    /** `{= 2 * level}` in a text: the result of a calculation, see [Expression]. A mistake leaves the token as written. */
    private fun calculate(source: String): Any? = try {
        Expression.format(Expression.parse(source).evaluate(this))
    } catch (error: ExpressionException) {
        logger.warning("Calculation {=$source} failed: ${error.message}")
        null
    }

    private inline fun named(name: String, lookup: (String) -> Any?): Any? =
        if (name == "player" || name == "player_name") player.name else lookup(name)

    /**
     * `{name}`, `{name|format}`, `{plugin/module:path}`, `{papi:name}`: this run's value first, then a pnLibrary
     * placeholder; `papi:` asks PlaceholderAPI. Without a placeholder service only this run's values are read.
     */
    private fun braced(expression: String): Any? {
        val name = expression.substringBefore('|').trim()
        if (name.startsWith(PAPI_PREFIX)) return external(name.removePrefix(PAPI_PREFIX))
        val service = placeholders ?: return runtimeValues[name]
        return service.resolve(expression, player.uniqueId, runtimeValues).toCompletableFuture().join()
    }

    private fun external(name: String): Any? {
        val adapter = placeholders?.adapters()?.get(EXTERNAL_ADAPTER) ?: return null
        if (!adapter.available) return null
        return adapter.resolve(player.uniqueId, name)
    }

    private companion object {
        const val EXTERNAL_ADAPTER = "placeholderapi"
        private const val PAPI_PREFIX = "papi:"

        /** `{value-or-placeholder}` and `%external%`, each with its own capture group. */
        val TOKEN = Regex("\\{(?![?:/])([^{}]+)}|%([A-Za-z0-9_.:-]+)%")
    }
}
