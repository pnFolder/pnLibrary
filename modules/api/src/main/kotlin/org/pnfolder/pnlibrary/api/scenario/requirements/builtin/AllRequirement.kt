package org.pnfolder.pnlibrary.api.scenario.requirements.builtin

import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict

/** Met when every requirement of [requirements] is met; the first one that is not decides the reason. */
data class AllRequirement(val requirements: List<Requirement> = emptyList()) : Requirement {
    override fun test(context: RequirementContext): Verdict =
        requirements.firstNotNullOfOrNull { it.test(context) as? Verdict.Unmet } ?: Verdict.Met
}
