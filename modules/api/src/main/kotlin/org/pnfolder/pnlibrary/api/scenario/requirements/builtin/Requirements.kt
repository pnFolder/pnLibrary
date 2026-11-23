package org.pnfolder.pnlibrary.api.scenario.requirements.builtin

import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.player.ItemQuery
import org.pnfolder.pnlibrary.api.scenario.expression.Expression
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionException
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict
import java.util.concurrent.ThreadLocalRandom

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

/** How [CompareRequirement] relates its two sides. */
enum class CompareOperator(internal val numeric: Boolean) {
    /** Equal; numbers are compared as numbers, anything else as text. */
    EQUALS(false),
    /** Not equal. */
    NOT_EQUALS(false),
    /** Left is greater than right; both must be numbers. */
    GREATER(true),
    /** Left is greater than or equal to right; both must be numbers. */
    GREATER_OR_EQUAL(true),
    /** Left is less than right; both must be numbers. */
    LESS(true),
    /** Left is less than or equal to right; both must be numbers. */
    LESS_OR_EQUAL(true),
    /** Left text contains right text. */
    CONTAINS(false),
    /** Left text starts with right text. */
    STARTS_WITH(false),
    /** Left text ends with right text. */
    ENDS_WITH(false),
    /** Left text matches the regular expression on the right as a whole. */
    MATCHES(false),
}

/**
 * Compares two values. Each side is text with placeholders, so it can be a literal, a context value,
 * a pnLibrary placeholder or an external one.
 *
 * ```yaml
 * type: compare
 * left: '%vault_eco_balance%'
 * operator: greater-or-equal
 * right: 100
 * ```
 *
 * @property left first side
 * @property operator relation between the sides
 * @property right second side
 * @property ignoreCase whether text is compared without case sensitivity
 */
data class CompareRequirement(
    val left: String = "",
    val operator: CompareOperator = CompareOperator.EQUALS,
    val right: String = "",
    val ignoreCase: Boolean = true,
) : Requirement {
    override fun test(context: RequirementContext): Verdict {
        val a = context.text(left).trim()
        val b = context.text(right).trim()
        val x = a.toBigDecimalOrNull()
        val y = b.toBigDecimalOrNull()
        if (operator.numeric && (x == null || y == null)) {
            return Verdict.Unmet("'${if (x == null) a else b}' is not a number")
        }
        val met = when (operator) {
            CompareOperator.EQUALS -> if (x != null && y != null) x.compareTo(y) == 0 else a.equals(b, ignoreCase)
            CompareOperator.NOT_EQUALS -> if (x != null && y != null) x.compareTo(y) != 0 else !a.equals(b, ignoreCase)
            CompareOperator.GREATER -> x!! > y!!
            CompareOperator.GREATER_OR_EQUAL -> x!! >= y!!
            CompareOperator.LESS -> x!! < y!!
            CompareOperator.LESS_OR_EQUAL -> x!! <= y!!
            CompareOperator.CONTAINS -> a.contains(b, ignoreCase)
            CompareOperator.STARTS_WITH -> a.startsWith(b, ignoreCase)
            CompareOperator.ENDS_WITH -> a.endsWith(b, ignoreCase)
            CompareOperator.MATCHES -> matches(a, b, context)
        }
        return Verdict.of(met, "$a is not ${operator.name.lowercase().replace('_', ' ')} $b")
    }

    private fun matches(text: String, pattern: String, context: RequirementContext): Boolean = try {
        Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()).matches(text)
    } catch (error: IllegalArgumentException) {
        context.logger.warning("Invalid regular expression '$pattern': ${error.message}")
        false
    }
}

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
        val have = context.player.countItems(ItemQuery(material, name, nameContains, loreContains, ignoreCase)) ?: 0
        return Verdict.of(have >= amount, "need $amount $material, have $have")
    }
}

/**
 * Met when the player has at least [amount] experience points, or levels with [level].
 *
 * @property amount required experience, not negative
 * @property level whether [amount] counts levels instead of points
 */
data class ExperienceRequirement(val amount: Int = 0, val level: Boolean = false) : Requirement {
    init { require(amount >= 0) { "Required experience must not be negative" } }

    override fun test(context: RequirementContext): Verdict {
        val have = context.player.experience(level) ?: return Verdict.Unmet("experience is not available here")
        return Verdict.of(have >= amount, "need $amount ${if (level) "levels" else "experience"}")
    }
}

/**
 * Met when the player is within [distance] blocks of [location].
 *
 * @property location point written as `world,x,y,z`
 * @property distance largest allowed distance in blocks, not negative
 * @throws IllegalArgumentException when [location] is not `world,x,y,z` or [distance] is negative
 */
data class NearRequirement(val location: String = "world,0,0,0", val distance: Double = 5.0) : Requirement {
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

    override fun test(context: RequirementContext): Verdict {
        val current = context.player.distanceTo(world, x, y, z) ?: return Verdict.Unmet("distance is not available here")
        return Verdict.of(current <= distance, "too far from $location")
    }
}

/** Met when every requirement of [requirements] is met; the first one that is not decides the reason. */
data class AllRequirement(val requirements: List<Requirement> = emptyList()) : Requirement {
    override fun test(context: RequirementContext): Verdict =
        requirements.firstNotNullOfOrNull { it.test(context) as? Verdict.Unmet } ?: Verdict.Met
}

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

/** Met when [requirement] is not. */
data class NotRequirement(val requirement: Requirement? = null) : Requirement {
    override fun test(context: RequirementContext): Verdict {
        val inner = requirement ?: return Verdict.Unmet("nothing to negate")
        return if (inner.test(context).isMet) Verdict.Unmet("not allowed") else Verdict.Met
    }
}

/**
 * Met when a calculation comes out true. This is the way to write anything the other types cannot: maths, the
 * player's level, placeholders, and any mix of them. See [Expression] for the whole language.
 *
 * ```yaml
 * type: expression
 * expression: "level() >= 10 && (permission('shop.vip') || {shop:discount} > 20)"
 * ```
 *
 * A mistake in the text is reported when the configuration loads. A calculation that fails at run time
 * (for example a text where a number is needed) is not met, and the reason says why.
 *
 * @property expression the calculation
 * @throws org.pnfolder.pnlibrary.api.scenario.expression.ExpressionException when the text cannot be parsed
 */
data class ExpressionRequirement(val expression: String = "true") : Requirement {
    private val parsed = Expression.parse(expression)

    override fun test(context: RequirementContext): Verdict = try {
        Verdict.of(parsed.test(context), "'$expression' is false")
    } catch (error: ExpressionException) {
        context.logger.warning("Expression '$expression' failed: ${error.message}")
        Verdict.Unmet("'$expression' could not be calculated: ${error.message}")
    }
}

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
