package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult
import java.util.concurrent.ThreadLocalRandom

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
