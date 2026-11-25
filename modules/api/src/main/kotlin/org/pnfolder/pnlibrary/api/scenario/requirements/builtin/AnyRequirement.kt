package org.pnfolder.pnlibrary.api.scenario.requirements.builtin

import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict

/** Met when at least one requirement of [requirements] is met; an empty list is never met. */
data class AnyRequirement(val requirements: List<Requirement> = emptyList()) : Requirement {
    override fun test(context: RequirementContext): Verdict {
        val reasons = ArrayList<String>()
        for (requirement in requirements) {
            when (val verdict = requirement.test(context)) {
                Verdict.Met -> return verdict
                is Verdict.Unmet -> reasons += verdict.reason
            }
        }
        return Verdict.Unmet(reasons.joinToString(" / ").ifEmpty { "nothing to choose from" })
    }
}
