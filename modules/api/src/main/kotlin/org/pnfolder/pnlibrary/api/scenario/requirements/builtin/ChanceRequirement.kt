package org.pnfolder.pnlibrary.api.scenario.requirements.builtin

import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict
import java.util.concurrent.ThreadLocalRandom

/**
 * Met by chance.
 *
 * @property probability from `0.0` (never) to `1.0` (always)
 * @throws IllegalArgumentException when [probability] is outside that range
 */
data class ChanceRequirement(val probability: Double = 0.5) : Requirement {
    init { require(probability in 0.0..1.0) { "Chance must be from 0.0 to 1.0" } }

    override fun test(context: RequirementContext): Verdict =
        Verdict.of(ThreadLocalRandom.current().nextDouble() < probability, "bad luck")
}
