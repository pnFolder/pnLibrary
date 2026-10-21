package org.pnfolder.pnlibrary.api.checks.builtin

import org.pnfolder.pnlibrary.api.audiences.ItemQuery
import org.pnfolder.pnlibrary.api.checks.Check
import org.pnfolder.pnlibrary.api.checks.CheckContext
import org.pnfolder.pnlibrary.api.checks.internal.CheckSerializer
import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.config.ConfigSerializeWith

/**
 * Matches when the player has at least [minimum] of the listed [permissions].
 *
 * Without [minimum] every listed permission is required. Blank entries are ignored, and an empty
 * list never matches.
 *
 * @property permissions permission nodes to check
 * @property minimum how many of them are enough, or `null` for all
 */
@ConfigSerializeWith(CheckSerializer::class)
data class PermissionsCheck(
    val permissions: List<String> = emptyList(),
    val minimum: Int? = null,
) : Check {
    override fun matches(context: CheckContext): Boolean {
        val nodes = permissions.filter(String::isNotBlank)
        if (nodes.isEmpty()) return false
        return nodes.count(context.player::hasPermission) >= (minimum ?: nodes.size)
    }
}

/**
 * Matches when the player carries at least [amount] items that fit the description.
 *
 * A platform without player inventories, or an unknown [material], never matches.
 *
 * @property material platform item name such as `diamond`
 * @property amount required count, at least one
 * @property name full display name the item must have, or `null`
 * @property nameContains text the display name must contain, or `null`
 * @property loreContains text a lore line must contain, or `null`
 * @property ignoreCase whether text is compared without case sensitivity
 * @throws IllegalArgumentException when [amount] is below one
 */
@ConfigSerializeWith(CheckSerializer::class)
data class ItemCheck(
    val material: String = "",
    val amount: Int = 1,
    val name: String? = null,
    @field:ConfigKey("name-contains")
    val nameContains: String? = null,
    @field:ConfigKey("lore-contains")
    val loreContains: String? = null,
    val ignoreCase: Boolean = true,
) : Check {
    init { require(amount >= 1) { "Required item amount must be at least 1" } }

    override fun matches(context: CheckContext): Boolean {
        if (material.isBlank()) return false
        val query = ItemQuery(material, name, nameContains, loreContains, ignoreCase)
        return (context.player.countItems(query) ?: 0) >= amount
    }
}

/**
 * Matches when the player has at least [amount] experience points, or levels when [level] is true.
 *
 * @property amount required experience, not negative
 * @property level whether [amount] counts levels instead of points
 * @throws IllegalArgumentException when [amount] is negative
 */
@ConfigSerializeWith(CheckSerializer::class)
data class ExperienceCheck(
    val amount: Int = 0,
    val level: Boolean = false,
) : Check {
    init { require(amount >= 0) { "Required experience must not be negative" } }

    override fun matches(context: CheckContext): Boolean {
        val current = context.player.experience(level) ?: return false
        return current >= amount
    }
}

/**
 * Matches when the player is within [distance] blocks of [location].
 *
 * @property location point written as `world,x,y,z`
 * @property distance largest allowed distance in blocks, not negative
 * @throws IllegalArgumentException when [location] is not `world,x,y,z` or [distance] is negative
 */
@ConfigSerializeWith(CheckSerializer::class)
data class NearCheck(
    val location: String = "world,0,0,0",
    val distance: Double = 5.0,
) : Check {
    private val world: String
    private val x: Double
    private val y: Double
    private val z: Double

    init {
        require(distance >= 0) { "Distance must not be negative" }
        val parts = location.split(',').map(String::trim)
        val numbers = parts.drop(1).map(String::toDoubleOrNull)
        require(parts.size == 4 && parts[0].isNotEmpty() && numbers.none { it == null }) {
            "Location must be written as world,x,y,z but was '$location'"
        }
        world = parts[0]
        x = numbers[0] ?: 0.0
        y = numbers[1] ?: 0.0
        z = numbers[2] ?: 0.0
    }

    override fun matches(context: CheckContext): Boolean {
        val current = context.player.distanceTo(world, x, y, z) ?: return false
        return current <= distance
    }
}
