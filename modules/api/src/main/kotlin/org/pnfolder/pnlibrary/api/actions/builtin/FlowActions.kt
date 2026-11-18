package org.pnfolder.pnlibrary.api.actions.builtin

import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.actions.ActionResult
import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.requirements.Requirement
import org.pnfolder.pnlibrary.api.requirements.Verdict
import org.pnfolder.pnlibrary.api.requirements.expression.Expression
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

/**
 * Runs [thenActions] when [requirement] is met, otherwise [elseActions]; the list goes on afterwards.
 * A failure inside the chosen branch fails the whole scenario.
 *
 * ```yaml
 * - type: if
 *   when: { type: expression, expression: "level() >= 10" }
 *   then: [ { type: message, messages: ['Veteran price'] } ]
 *   else: [ { type: message, messages: ['Normal price'] } ]
 * ```
 *
 * @property requirement the question; a missing one is never met
 */
data class IfAction(
    @field:ConfigKey("when")
    val requirement: Requirement? = null,
    @field:ConfigKey("then")
    val thenActions: List<Action> = emptyList(),
    @field:ConfigKey("else")
    val elseActions: List<Action> = emptyList(),
) : Action {
    override fun run(context: ActionContext): ActionResult {
        val met = requirement?.test(context)?.isMet ?: false
        return context.runList(if (met) thenActions else elseActions)
    }
}

/**
 * Ends the scenario quietly when [requirement] is not met, after running [deny].
 *
 * Inside [deny] the reason is available as `[reason]`.
 *
 * ```yaml
 * - type: require
 *   when: { type: permission, permission: shop.use }
 *   deny: [ { type: message, messages: ['&c[reason]'] } ]
 * ```
 *
 * @property requirement what must hold; a missing one is never met
 * @property deny actions run when it does not
 */
data class RequireAction(
    @field:ConfigKey("when")
    val requirement: Requirement? = null,
    val deny: List<Action> = emptyList(),
) : Action {
    override fun run(context: ActionContext): ActionResult {
        val verdict = requirement?.test(context) ?: Verdict.Unmet("no requirement is configured")
        if (verdict !is Verdict.Unmet) return ActionResult.Done
        context.setValue("reason", verdict.reason)
        val denied = context.runList(deny)
        return if (denied is ActionResult.Failed) denied else ActionResult.Stopped(verdict.reason)
    }
}

/**
 * One reaction of an [AttemptAction] to a kind of failure.
 *
 * A code matches exactly (`currency.insufficient-funds`), as a family (`currency.*`), or everything (`*`).
 *
 * @property codes failure codes this handler takes; empty takes every failure
 * @property steps actions run for it, written under `do`
 */
data class ErrorHandler(
    val codes: List<String> = emptyList(),
    @field:ConfigKey("do")
    val steps: List<Action> = emptyList(),
) {
    /** Whether this handler takes a failure with [code]. */
    fun matches(code: String): Boolean = codes.isEmpty() || codes.any { pattern ->
        pattern == "*" || pattern == code || (pattern.endsWith(".*") && code.startsWith(pattern.dropLast(1)))
    }
}

/**
 * Runs [steps] as a unit that may fail. When a step fails, everything that unit did is undone (a charge is given
 * back), then the first of [handlers] that takes the failure's code runs, or [fail] when none does.
 * The scenario then ends, or goes on when [resume] is set.
 *
 * Inside a handler the cause is available as `[error]`, its code as `[error_code]`.
 *
 * ```yaml
 * - type: attempt
 *   do:
 *     - { type: charge, currency: money, amount: 100 }
 *     - { type: command, command: 'give [player] diamond 1' }
 *   on:
 *     - codes: [currency.insufficient-funds]
 *       do: [ { type: message, messages: ['&cYou need 100 coins'] } ]
 *     - codes: ['currency.*']
 *       do: [ { type: message, messages: ['&cThe bank is not answering, try later'] } ]
 *   fail:
 *     - { type: message, messages: ['&cCould not buy: [error]'] }
 * ```
 *
 * @property steps the actions that may fail
 * @property handlers reactions by failure code, the first match wins
 * @property fail actions run for a failure no handler takes
 * @property resume whether the rest of the list runs after a handled failure
 * @property undo whether the steps are undone on a failure; with `false` what they did stays, and an outer
 * failure does not undo it either
 */
data class AttemptAction(
    @field:ConfigKey("do")
    val steps: List<Action> = emptyList(),
    @field:ConfigKey("on")
    val handlers: List<ErrorHandler> = emptyList(),
    val fail: List<Action> = emptyList(),
    val resume: Boolean = false,
    val undo: Boolean = true,
) : Action {
    override fun run(context: ActionContext): ActionResult {
        val mark = context.undoMark()
        val result = context.runList(steps)
        if (result !is ActionResult.Failed) return result
        if (undo) context.rollbackTo(mark) else context.discardUndo(mark)
        context.setValue("error", result.message).setValue("error_code", result.code)
        val reaction = handlers.firstOrNull { it.matches(result.code) }?.steps ?: fail
        val handled = context.runList(reaction)
        return when {
            handled is ActionResult.Failed -> handled
            resume -> ActionResult.Done
            else -> ActionResult.Stopped(result.message)
        }
    }
}

/**
 * Runs [steps] after [after] without waiting; the scenario goes on at once.
 *
 * The delayed part is its own scenario: a failure there is logged and undoes only its own steps.
 *
 * @property after the pause
 * @property steps what runs afterwards
 */
data class DelayAction(
    val after: Duration = Duration.ofSeconds(1),
    @field:ConfigKey("then")
    val steps: List<Action> = emptyList(),
) : Action {
    init { require(!after.isNegative) { "Delay must not be negative" } }

    override fun run(context: ActionContext): ActionResult {
        context.runLater(after, steps)
        return ActionResult.Done
    }
}

/**
 * Runs [steps] [times] times, [every] apart. The first run is immediate and part of the scenario; later runs
 * are separate scenarios.
 *
 * @property times how many runs, from 1 to [MAX_TIMES]
 * @property every pause between runs
 * @property steps what runs each time
 */
data class RepeatAction(
    val times: Int = 1,
    val every: Duration = Duration.ZERO,
    @field:ConfigKey("then")
    val steps: List<Action> = emptyList(),
) : Action {
    init {
        require(times in 1..MAX_TIMES) { "Repeat times must be from 1 to $MAX_TIMES" }
        require(!every.isNegative) { "Repeat interval must not be negative" }
    }

    override fun run(context: ActionContext): ActionResult {
        for (run in 1 until times) context.runLater(every.multipliedBy(run.toLong()), steps)
        return context.runList(steps)
    }

    /** Limits for repeat actions. */
    companion object {
        /** The most runs one repeat may schedule. */
        const val MAX_TIMES = 1000
    }
}

/**
 * Ends the scenario here, quietly and without undoing anything.
 *
 * @property reason optional note for the result
 */
data class StopAction(val reason: String? = null) : Action {
    override fun run(context: ActionContext): ActionResult = ActionResult.Stopped(reason)
}

/**
 * Stores a value for the steps after it, readable as `[name]`. Give either a [value] (text with placeholders) or
 * an [expression] (a calculation, see [Expression]).
 *
 * ```yaml
 * - { type: set, name: price, expression: "round(base * (1 - {shop:discount} / 100), 2)" }
 * - { type: message, messages: ['Price: [price]'] }
 * ```
 *
 * @property name the value's name
 * @property value text to store, with placeholders filled in
 * @property expression calculation to store; wins over [value]
 * @throws org.pnfolder.pnlibrary.api.requirements.expression.ExpressionException when [expression] cannot be parsed
 */
data class SetAction(
    val name: String = "",
    val value: String = "",
    val expression: String? = null,
) : Action {
    private val parsed = expression?.let(Expression::parse)

    override fun run(context: ActionContext): ActionResult {
        if (name.isBlank()) return ActionResult.Failed("set.no-name", "A set action needs a name")
        val result = parsed?.evaluate(context) ?: context.format(value)
        context.setValue(name, result)
        return ActionResult.Done
    }
}

/**
 * Runs an action a plugin defined by name (see `ActionService.define`), giving it [args] as values for the call.
 * The arguments are visible inside the call only. Calls may nest to [MAX_DEPTH] levels.
 *
 * ```yaml
 * - { type: call, name: give-kit, args: { kit: starter, amount: '{= 2 * level()}' } }
 * ```
 *
 * @property name the defined name
 * @property args values for the call; placeholders in them are filled in first
 */
data class CallAction(
    val name: String = "",
    val args: Map<String, String> = emptyMap(),
) : Action {
    override fun run(context: ActionContext): ActionResult {
        val target = context.action(name) ?: return ActionResult.Failed("call.unknown", "Action '$name' is not defined")
        val depth = (context.value(DEPTH) as? Number)?.toInt() ?: 0
        if (depth >= MAX_DEPTH) return ActionResult.Failed("call.too-deep", "Action '$name' is called too deeply; check for a loop")
        val values = args.mapValues { context.format(it.value) } + (DEPTH to depth + 1)
        return context.scoped(values) { target.run(context) }
    }

    /** Limits for calls. */
    companion object {
        /** The deepest chain of calls inside calls. */
        const val MAX_DEPTH = 16
        private const val DEPTH = "_call_depth"
    }
}

/** One choice of a [RandomAction]. */
data class RandomOption(
    val weight: Double = 1.0,
    @field:ConfigKey("do")
    val steps: List<Action> = emptyList(),
) {
    init { require(weight > 0) { "A weight must be positive" } }
}

/**
 * Runs one of [options] chosen by chance; a bigger `weight` is chosen more often.
 *
 * ```yaml
 * - type: random
 *   options:
 *     - { weight: 70, do: [ { type: message, messages: ['Common'] } ] }
 *     - { weight: 30, do: [ { type: message, messages: ['Rare'] } ] }
 * ```
 *
 * @property options the choices; with none, nothing happens
 */
data class RandomAction(val options: List<RandomOption> = emptyList()) : Action {
    override fun run(context: ActionContext): ActionResult {
        if (options.isEmpty()) return ActionResult.Done
        var roll = ThreadLocalRandom.current().nextDouble() * options.sumOf(RandomOption::weight)
        val chosen = options.firstOrNull { roll -= it.weight; roll < 0 } ?: options.last()
        return context.runList(chosen.steps)
    }
}
