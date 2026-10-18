package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.config.ConfigKey

/**
 * Selects one action branch using the string form of a context value.
 *
 * The selected case is executed once. When no case matches, [defaultActions] is executed.
 * Branches are ordinary action lists and may contain nested switches,
 * conditions, sequences, delays, and value operations.
 *
 * @property source context key whose value is used for selection
 * @property cases case value to action list mapping
 * Case names are never reserved because the fallback is configured separately.
 * @property defaultActions fallback branch executed when no case matches
 * @property ignoreCase whether case keys are matched without case sensitivity
 */
data class SwitchAction(
    @field:ConfigKey("on")
    val source: String = "",
    val cases: Map<String, List<Action>> = emptyMap(),
    @field:ConfigKey("default")
    val defaultActions: List<Action> = emptyList(),
    val ignoreCase: Boolean = true,
) : Action {
    override fun execute(context: ActionContext) {
        val actual = context.value(source)?.toString()
        val entry = cases.entries.firstOrNull { (expected, _) ->
            if (ignoreCase) expected.equals(actual, ignoreCase = true) else expected == actual
        }
        context.execute(entry?.value ?: defaultActions)
    }
}
