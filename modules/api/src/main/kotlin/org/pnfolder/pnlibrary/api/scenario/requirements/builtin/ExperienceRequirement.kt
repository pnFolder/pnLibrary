package org.pnfolder.pnlibrary.api.scenario.requirements.builtin

import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict

/**
 * Met when the player has at least [amount] experience points, or levels with [level].
 *
 * @property amount required experience, not negative
 * @property level whether [amount] counts levels instead of points
 */
data class ExperienceRequirement(val amount: Int = 0, val level: Boolean = false) : Requirement {
    init { require(amount >= 0) { "Required experience must not be negative" } }

    override fun test(context: RequirementContext): Verdict {
        val have = (if (level) context.player.experienceLevel() else context.player.experiencePoints()) ?: return Verdict.Unmet("experience is not available here")
        return Verdict.of(have >= amount, "need $amount ${if (level) "levels" else "experience"}")
    }
}
