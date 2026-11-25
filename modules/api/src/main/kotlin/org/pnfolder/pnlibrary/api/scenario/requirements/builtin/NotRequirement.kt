package org.pnfolder.pnlibrary.api.scenario.requirements.builtin

import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict

/** Met when [requirement] is not. */
data class NotRequirement(val requirement: Requirement? = null) : Requirement {
    override fun test(context: RequirementContext): Verdict {
        val inner = requirement ?: return Verdict.Unmet("nothing to negate")
        return if (inner.test(context).isMet) Verdict.Unmet("not allowed") else Verdict.Met
    }
}
