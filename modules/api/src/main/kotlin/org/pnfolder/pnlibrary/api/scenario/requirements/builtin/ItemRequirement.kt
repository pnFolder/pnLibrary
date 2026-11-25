package org.pnfolder.pnlibrary.api.scenario.requirements.builtin

import org.pnfolder.pnlibrary.api.world.minecraftKey
import org.pnfolder.pnlibrary.api.world.ItemMatcher
import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict

/**
 * Met when the player carries at least [amount] items that fit the description.
 *
 * A platform without player inventories never meets it.
 *
 * @property material platform item name such as `diamond`
 * @property amount required count, at least one
 * @property name full display name the item must have, or `null`
 * @property nameContains text the display name must contain, or `null`
 * @property loreContains text a lore line must contain, or `null`
 * @property ignoreCase whether text is compared without case sensitivity
 */
data class ItemRequirement(
    val material: String = "",
    val amount: Int = 1,
    val name: String? = null,
    @field:ConfigKey("name-contains")
    val nameContains: String? = null,
    @field:ConfigKey("lore-contains")
    val loreContains: String? = null,
    val ignoreCase: Boolean = true,
) : Requirement {
    init { require(amount >= 1) { "Required item amount must be at least 1" } }

    override fun test(context: RequirementContext): Verdict {
        if (material.isBlank()) return Verdict.Unmet("no item is configured")
        val have = context.player.countItems(ItemMatcher(minecraftKey(material), name, nameContains, loreContains, ignoreCase)) ?: 0
        return Verdict.of(have >= amount, "need $amount $material, have $have")
    }
}
