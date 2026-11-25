package org.pnfolder.pnlibrary.api.scenario.requirements.builtin

import org.pnfolder.pnlibrary.api.audience.hasPermission
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict

/**
 * Met when the player has the listed permissions: all of them, or any one with [any].
 *
 * ```yaml
 * type: permission
 * permission: shop.use          # or: permissions: [shop.use, shop.vip]
 * ```
 *
 * @property permission one permission node, may be blank when [permissions] is used
 * @property permissions more permission nodes
 * @property any whether one permission is enough instead of all of them
 */
data class PermissionRequirement(
    val permission: String = "",
    val permissions: List<String> = emptyList(),
    val any: Boolean = false,
) : Requirement {
    override fun test(context: RequirementContext): Verdict {
        val nodes = (listOf(permission) + permissions).filter(String::isNotBlank)
        if (nodes.isEmpty()) {
            context.logger.warning("A permission requirement lists no permission and is never met")
            return Verdict.Unmet("no permission is configured")
        }
        val missing = nodes.filterNot(context.player::hasPermission)
        val met = if (any) missing.size < nodes.size else missing.isEmpty()
        return Verdict.of(met, "missing permission ${missing.joinToString()}")
    }
}
