package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.scenario.actions.Action

/** One choice of a [RandomAction]. */
data class RandomOption(
    val weight: Double = 1.0,
    @field:ConfigKey("do")
    val steps: List<Action> = emptyList(),
) {
    init { require(weight > 0) { "A weight must be positive" } }
}
