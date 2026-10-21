package org.pnfolder.pnlibrary.api.actions

import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.config.ConfigRange
import org.pnfolder.pnlibrary.api.config.ConfigSerializationContext
import org.pnfolder.pnlibrary.api.config.ConfigSerializeWith
import org.pnfolder.pnlibrary.api.config.ConfigSerializer

/**
 * One named check of a [RequirementsAction].
 *
 * In configuration it is one object: the condition fields (`type`, `permission`, `input`, …) next to
 * `optional`, `deny_commands` and `success_commands`.
 *
 * @property check condition that must match
 * @property optional whether the check only counts toward `minimum_requirements`
 * @property success actions executed when the whole requirements action passes and this check matched
 * @property deny actions executed when this check fails; ignored for optional checks
 */
@ConfigSerializeWith(RequirementSerializer::class)
data class Requirement(
    val check: ActionCondition? = null,
    val optional: Boolean = false,
    val success: List<Action> = emptyList(),
    val deny: List<Action> = emptyList(),
)

/**
 * Checks named requirements and runs `success_commands` or `deny_commands`, the way menu click requirements work.
 *
 * Checks run in declaration order. Every check that is not optional must match: the first one that
 * fails runs its own `deny_commands` and then the shared ones, and the whole invocation ends, so actions
 * after this one never run. Optional checks do not have to match, but at least `minimum_requirements` of
 * them must. With `stop_at_success`, optional checks are skipped once that many matched. When everything
 * passes, the `success_commands` of the matched checks run, then the shared ones.
 *
 * A check without a condition, or whose condition throws, counts as failed and a warning is written to
 * the action logger.
 *
 * @property requirements named checks in the order they are checked
 * @property minimum how many optional checks must match, zero or more
 * @property stopAtSuccess whether optional checks are skipped once [minimum] of them matched
 * @property success actions executed after the success actions of the matched checks
 * @property deny actions executed after a failed check's own deny actions
 * @throws IllegalArgumentException when built in code with a [minimum] that is negative or above the number of
 * optional checks; in configuration the same mistake is a warning when the action runs
 */
data class RequirementsAction(
    val requirements: Map<String, Requirement> = emptyMap(),
    @field:ConfigKey("minimum_requirements")
    @field:ConfigRange(min = 0.0)
    val minimum: Int = 0,
    @field:ConfigKey("stop_at_success")
    val stopAtSuccess: Boolean = false,
    @field:ConfigKey("success_commands")
    val success: List<Action> = emptyList(),
    @field:ConfigKey("deny_commands")
    val deny: List<Action> = emptyList(),
) : Action {
    init {
        require(minimum >= 0) { "minimum_requirements must not be negative" }
        require(minimum <= requirements.values.count(Requirement::optional)) {
            "minimum_requirements ($minimum) is more than the number of optional checks"
        }
    }

    override fun execute(context: ActionContext) {
        // init only runs for actions built in code; values read from YAML are set afterwards, so check again here
        val optionalCount = requirements.values.count(Requirement::optional)
        if (minimum > optionalCount) {
            context.logger.warning(
                "minimum_requirements ($minimum) is more than the number of optional checks ($optionalCount); this action can never pass",
            )
        }
        val matched = ArrayList<Requirement>()
        var optionalMatched = 0
        for ((name, requirement) in requirements) {
            if (stopAtSuccess && minimum > 0 && optionalMatched >= minimum && requirement.optional) continue
            if (matches(context, name, requirement)) {
                matched += requirement
                if (requirement.optional) optionalMatched++
            } else if (!requirement.optional) {
                denied(context, requirement.deny)
                return
            }
        }
        if (optionalMatched < minimum) {
            denied(context, emptyList())
            return
        }
        matched.forEach { context.execute(it.success) }
        context.execute(success)
    }

    private fun denied(context: ActionContext, own: List<Action>) {
        context.execute(own)
        context.execute(deny)
        context.stop()
    }

    private fun matches(context: ActionContext, name: String, requirement: Requirement): Boolean {
        val condition = requirement.check
        if (condition == null) {
            context.logger.warning("Requirement '$name' has no type and counts as failed")
            return false
        }
        return try {
            condition.matches(context)
        } catch (error: RuntimeException) {
            context.logger.warning("Requirement '$name' could not be checked: ${error.message}")
            false
        }
    }
}

/**
 * Reads and writes a [Requirement] in the shapes menu plugins use, plus two shorter ones.
 *
 * - Full: `type`, its fields, `deny_commands` and `success_commands`.
 * - Expression: `if: "%vault_eco_balance% >= 100"` in place of `type`, see [RequirementExpression].
 * - Short answers: `deny: "&cNeed 100 coins"` is a chat message, `deny: [...]` is a list of `[tag]` lines or
 *   plain messages. `success` works the same way.
 * - Text only: `rich: "%vault_eco_balance% >= 100"` is a requirement with nothing to run.
 */
object RequirementSerializer : ConfigSerializer<Requirement> {
    private val reserved = listOf("optional", "deny_commands", "success_commands", "deny", "success", "if")

    override fun deserialize(value: Any?, context: ConfigSerializationContext): Requirement {
        if (value is String) return Requirement(check = RequirementExpression.parse(value))
        require(value is Map<*, *>) { "A requirement must be a text such as '%x% >= 5', or an object with a 'type' or an 'if'" }
        val fields = value.entries.associate { it.key.toString() to it.value }
        val lower = fields.mapKeys { it.key.lowercase() }
        val expression = lower["if"]
        val check = if (expression != null) {
            require(!lower.containsKey("type")) { "Use either 'if' or 'type', not both" }
            fields.keys.firstOrNull { it.lowercase() !in reserved }?.let { key ->
                throw IllegalArgumentException(
                    "Unknown key '$key' next to 'if'.${Suggestions.hint(key, reserved)} Valid keys: ${reserved.joinToString()}.",
                )
            }
            RequirementExpression.parse(expression.toString())
        } else {
            ConditionSerializer.parse(fields.filterKeys { it.lowercase() !in reserved }, reserved.toSet())
        }
        return Requirement(
            check = check,
            optional = lower["optional"] == true || lower["optional"].toString().equals("true", ignoreCase = true),
            success = actions(lower, "success_commands", "success"),
            deny = actions(lower, "deny_commands", "deny"),
        )
    }

    override fun serialize(value: Requirement, context: ConfigSerializationContext): Any? {
        val condition = value.check?.let(ConditionSerializer::describe)
            ?: throw UnsupportedOperationException("A requirement without a writable condition cannot be saved")
        val out = LinkedHashMap<String, Any?>(condition)
        if (value.optional) out["optional"] = true
        if (value.success.isNotEmpty()) out["success_commands"] = value.success.map(::line)
        if (value.deny.isNotEmpty()) out["deny_commands"] = value.deny.map(::line)
        return out
    }

    /** `long` keeps the strict menu-plugin form (every entry a `[tag]` line); `short` also accepts plain messages. */
    private fun actions(fields: Map<String, Any?>, long: String, short: String): List<Action> {
        require(!(fields.containsKey(long) && fields.containsKey(short))) { "Use either '$short' or '$long', not both" }
        return if (fields.containsKey(long)) lines(fields[long], long) else shortLines(fields[short], short)
    }

    private fun lines(source: Any?, key: String): List<Action> = when (source) {
        null -> emptyList()
        is String -> listOf(ActionLine.parse(source))
        is Iterable<*> -> source.map {
            ActionLine.parse(it as? String ?: throw IllegalArgumentException("Every entry of $key must be a [tag] line"))
        }
        else -> throw IllegalArgumentException("$key must be a list of [tag] lines")
    }

    private fun shortLines(source: Any?, key: String): List<Action> = when (source) {
        null -> emptyList()
        is String -> listOf(textOrLine(source))
        is Iterable<*> -> source.map {
            textOrLine(it as? String ?: throw IllegalArgumentException("Every entry of $key must be text"))
        }
        else -> throw IllegalArgumentException("$key must be a text or a list of texts")
    }

    private fun textOrLine(text: String): Action =
        if (text.trimStart().startsWith("[")) ActionLine.parse(text) else MessageAction(listOf(text))

    private fun line(action: Action): String = ActionLine.format(action)
        ?: throw UnsupportedOperationException("${action.javaClass.simpleName} cannot be written as a [tag] line")
}
