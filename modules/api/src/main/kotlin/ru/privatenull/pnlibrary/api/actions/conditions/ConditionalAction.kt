package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.config.ConfigKey

/**
 * Evaluates an explicit `all` or `any` check list and executes one branch.
 *
 * When [allChecks] is not empty, every item must match. Otherwise at least one item
 * in [anyChecks] must match. Empty check lists select [elseActions]. Branch actions
 * receive the original [ActionContext] and execute synchronously.
 *
 * @property allChecks checks that must all match
 * @property anyChecks checks where one match is sufficient
 * @property thenActions branch executed when the combined result is true
 * @property elseActions branch executed when the combined result is false
 */
data class ConditionalAction(
    @field:ConfigKey("all")
    val allChecks: List<ActionCondition> = emptyList(),
    @field:ConfigKey("any")
    val anyChecks: List<ActionCondition> = emptyList(),
    @field:ConfigKey("then")
    val thenActions: List<Action> = emptyList(),
    @field:ConfigKey("else")
    val elseActions: List<Action> = emptyList(),
) : Action {
    override fun execute(context: ActionContext) {
        require(allChecks.isEmpty() || anyChecks.isEmpty()) {
            "An if action must use either 'all' or 'any', not both"
        }
        val matches = when {
            allChecks.isNotEmpty() -> allChecks.all { it.matches(context) }
            anyChecks.isNotEmpty() -> anyChecks.any { it.matches(context) }
            else -> false
        }
        context.execute(if (matches) thenActions else elseActions)
    }
}
