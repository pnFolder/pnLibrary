package org.pnfolder.pnlibrary.api.scenario.requirements.builtin

import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict

/**
 * Met when the requirement a plugin defined under [name] (see `ActionService.defineRequirement`) is met.
 *
 * @property name name given when the requirement was defined
 */
data class RefRequirement(val name: String = "") : Requirement {
    override fun test(context: RequirementContext): Verdict {
        val target = context.requirement(name) ?: return Verdict.Unmet("requirement '$name' is not defined")
        return target.test(context)
    }
}
