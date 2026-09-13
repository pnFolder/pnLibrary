package ru.privatenull.pnlibrary.api.actions

enum class ConditionMode { ALL, ANY }

/** Selects one action branch without requiring an artificial zero-duration delay. */
data class ConditionalAction(
    val conditions: List<ActionCondition> = emptyList(),
    val mode: ConditionMode = ConditionMode.ALL,
    val actions: List<Action> = emptyList(),
    val otherwise: List<Action> = emptyList(),
) : Action {
    override fun execute(context: ActionContext) {
        val matches = when (mode) {
            ConditionMode.ALL -> conditions.all { it.matches(context) }
            ConditionMode.ANY -> conditions.any { it.matches(context) }
        }
        context.execute(if (matches) actions else otherwise)
    }
}
