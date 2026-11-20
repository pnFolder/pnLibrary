package org.pnfolder.pnlibrary.api.scenario.requirements

import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic
import org.pnfolder.pnlibrary.api.config.ConfigType
import org.pnfolder.pnlibrary.api.config.ConfigTypes
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.AllRequirement
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.AnyRequirement
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.ChanceRequirement
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.CompareRequirement
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.ExperienceRequirement
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.ExpressionRequirement
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.ItemRequirement
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.NearRequirement
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.NotRequirement
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.PermissionRequirement
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.RefRequirement

/**
 * A condition that only looks: it answers [Verdict.Met] or [Verdict.Unmet] and changes nothing.
 *
 * A requirement is a configuration value with a `type`. Values it compares can come from anywhere
 * [RequirementContext.text] reads: a literal, a context value `[name]`, a pnLibrary placeholder
 * `{plugin:name}` or an external one `%name%`.
 *
 * ```yaml
 * when:
 *   type: all
 *   requirements:
 *     - type: permission
 *       permission: shop.use
 *     - type: compare
 *       left: '%vault_eco_balance%'
 *       operator: greater-or-equal
 *       right: 100
 * ```
 *
 * Money, items and other things that must be taken are not requirements: use an action that can fail,
 * such as `charge`, so that the taking and the checking cannot disagree.
 *
 * Kotlin and Java code can implement the interface directly:
 *
 * ```kotlin
 * val vip = Requirement { ctx -> Verdict.of(ctx.player.hasPermission("vip"), "VIP only") }
 * ```
 *
 * Implementations should be free of side effects, because one requirement may run many times.
 */
@ConfigPolymorphic(discriminator = "type")
@ConfigTypes(
    ConfigType(PermissionRequirement::class, "permission", aliases = ["permissions"]),
    ConfigType(CompareRequirement::class, "compare"),
    ConfigType(ChanceRequirement::class, "chance"),
    ConfigType(ItemRequirement::class, "item", aliases = ["items"]),
    ConfigType(ExperienceRequirement::class, "experience", aliases = ["exp"]),
    ConfigType(NearRequirement::class, "near"),
    ConfigType(AllRequirement::class, "all", aliases = ["and"]),
    ConfigType(AnyRequirement::class, "any", aliases = ["or"]),
    ConfigType(NotRequirement::class, "not"),
    ConfigType(ExpressionRequirement::class, "expression", aliases = ["expr", "calc"]),
    ConfigType(RefRequirement::class, "ref", aliases = ["defined"]),
)
fun interface Requirement {
    /** Judges [context]; an [Verdict.Unmet] carries the reason that can be shown to the player. */
    fun test(context: RequirementContext): Verdict
}
