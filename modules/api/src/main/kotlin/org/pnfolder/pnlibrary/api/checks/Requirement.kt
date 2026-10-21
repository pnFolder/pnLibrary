package org.pnfolder.pnlibrary.api.checks

import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.checks.internal.RequirementEntrySerializer
import org.pnfolder.pnlibrary.api.checks.internal.RequirementSerializer
import org.pnfolder.pnlibrary.api.config.ConfigSerializeWith

/**
 * A check with what to do on "yes" and "no": one entry of the `checks:` block of a plugin.
 *
 * The short form is one [Check] under `check:`. The DeluxeMenus form lists named [entries] under
 * `requirements:`, where every entry must match unless it is `optional`, and at least [minimum] optional
 * entries must match.
 *
 * ```yaml
 * checks:
 *   has-100:
 *     check:
 *       type: '>='
 *       input: '%vault_eco_balance%'
 *       output: 100
 *     deny:
 *       - type: message
 *         message: '&cNeed 100 coins'
 * ```
 *
 * @property entries checks in the order they are evaluated
 * @property minimum how many optional entries must match, zero or more
 * @property stopAtSuccess whether optional entries are skipped once [minimum] of them matched
 * @property success actions run after a pass, after the `success` of the matched entries
 * @property deny actions run after a failure, after the `deny` of the failed entry
 */
@ConfigSerializeWith(RequirementSerializer::class)
data class Requirement(
    val entries: Map<String, Entry> = emptyMap(),
    val minimum: Int = 0,
    val stopAtSuccess: Boolean = false,
    val success: List<Action> = emptyList(),
    val deny: List<Action> = emptyList(),
) {
    /** A requirement made of the single [check]. */
    constructor(check: Check, deny: List<Action> = emptyList(), success: List<Action> = emptyList()) :
        this(mapOf(SINGLE to Entry(check)), success = success, deny = deny)

    init {
        require(minimum >= 0) { "minimum_requirements must not be negative" }
    }

    /**
     * One named check of a [Requirement].
     *
     * @property check the check; `null` counts as failed with a warning
     * @property optional whether the entry only counts toward [Requirement.minimum]
     * @property success actions run when the whole requirement passes and this entry matched
     * @property deny actions run when this entry fails; ignored for optional entries
     */
    @ConfigSerializeWith(RequirementEntrySerializer::class)
    data class Entry(
        val check: Check? = null,
        val optional: Boolean = false,
        val success: List<Action> = emptyList(),
        val deny: List<Action> = emptyList(),
    )

    /** Answers "yes" or "no" without running anything. */
    fun check(context: CheckContext): Boolean = evaluate(context).passed

    /**
     * Answers "yes" or "no" and runs the matching actions: on "yes" the `success` of the matched entries and
     * then [success]; on "no" the `deny` of the failed entry and then [deny]. The caller decides what else stops.
     */
    fun test(context: ActionContext): Boolean {
        val result = evaluate(context)
        if (result.passed) {
            result.matched.forEach { context.execute(it.success) }
            context.execute(success)
        } else {
            context.execute(result.failed?.deny.orEmpty())
            context.execute(deny)
        }
        return result.passed
    }

    private class Result(val passed: Boolean, val matched: List<Entry>, val failed: Entry?)

    private fun evaluate(context: CheckContext): Result {
        val optionalCount = entries.values.count(Entry::optional)
        if (minimum > optionalCount) {
            context.logger.warning(
                "minimum_requirements ($minimum) is more than the number of optional checks ($optionalCount); it can never pass",
            )
        }
        val matched = ArrayList<Entry>()
        var optionalMatched = 0
        for ((name, entry) in entries) {
            if (stopAtSuccess && minimum > 0 && optionalMatched >= minimum && entry.optional) continue
            if (matches(context, name, entry)) {
                matched += entry
                if (entry.optional) optionalMatched++
            } else if (!entry.optional) {
                return Result(false, matched, entry)
            }
        }
        return Result(optionalMatched >= minimum, matched, null)
    }

    private fun matches(context: CheckContext, name: String, entry: Entry): Boolean {
        val check = entry.check
        if (check == null) {
            context.logger.warning("Check '$name' has no type and counts as failed")
            return false
        }
        return try {
            check.matches(context)
        } catch (error: RuntimeException) {
            context.logger.warning("Check '$name' could not be evaluated: ${error.message}")
            false
        }
    }

    private companion object {
        const val SINGLE = "check"
    }
}
