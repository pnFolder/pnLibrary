package org.pnfolder.pnlibrary.api.actions

import org.pnfolder.pnlibrary.api.config.ConfigSerializationContext
import org.pnfolder.pnlibrary.api.config.ConfigSerializer
import java.util.Locale

/**
 * Reads and writes conditions in the requirement format used by menu plugins.
 *
 * A condition is an object with a `type` and the fields of that type. A `!` before the type negates it.
 * Key spelling does not matter: `name_contains`, `name-contains` and `nameContains` are the same key.
 *
 * | `type` | Fields |
 * |---|---|
 * | `has permission` | `permission` |
 * | `has permissions` | `permissions`, `minimum` |
 * | `has item` | `material`, `amount`, `name`, `name_contains`, `lore_contains`, `name_ignorecase` |
 * | `has exp` | `amount`, `level` |
 * | `is near` | `location` as `world,x,y,z`, `distance` |
 * | `string equals`, `string equals ignorecase`, `string contains` | `input`, `output` |
 * | `string length` | `input`, `min`, `max` |
 * | `is object` | `input`, `object` as `INT`, `DOUBLE` or `UUID` |
 * | `regex matches` | `input`, `regex` |
 * | `==`, `!=`, `>=`, `<=`, `>`, `<` | `input`, `output`, compared as numbers |
 */
object ConditionSerializer : ConfigSerializer<ActionCondition> {
    internal val comparators = mapOf(
        "==" to Comparison.NUMBER_EQUALS,
        "!=" to Comparison.NUMBER_NOT_EQUALS,
        ">=" to Comparison.GREATER_OR_EQUAL,
        "<=" to Comparison.LESS_OR_EQUAL,
        ">" to Comparison.GREATER_THAN,
        "<" to Comparison.LESS_THAN,
    )

    override fun deserialize(value: Any?, context: ConfigSerializationContext): ActionCondition {
        require(value is Map<*, *>) { "A condition must be an object with a 'type'" }
        return parse(value.entries.associate { it.key.toString() to it.value })
    }

    override fun serialize(value: ActionCondition, context: ConfigSerializationContext): Any? =
        describe(value) ?: throw UnsupportedOperationException(
            "${value.javaClass.simpleName} cannot be written to configuration",
        )

    /**
     * Builds a condition from the fields of one object, including its `type`.
     *
     * @throws IllegalArgumentException when the type is missing or unknown or a field is invalid
     */
    fun parse(fields: Map<String, Any?>, alsoValid: Set<String> = emptySet()): ActionCondition {
        val reader = Fields(fields)
        val rawType = reader.text("type") ?: throw IllegalArgumentException("A condition needs a 'type'")
        val negated = rawType.trim().startsWith("!")
        val type = rawType.trim().removePrefix("!").trim().let(::normalizeType)
        checkKeys(type, fields.keys, alsoValid)
        val condition = build(type, reader)
        return if (negated) NotCondition(condition) else condition
    }

    /** Writes [condition] as an object with a `type`, or returns `null` when it has no such form. */
    fun describe(condition: ActionCondition): Map<String, Any?>? = when (condition) {
        is PermissionCondition -> mapOf("type" to "has permission", "permission" to condition.permission)
        is PermissionsCondition -> mapOf(
            "type" to "has permissions",
            "permissions" to condition.permissions,
            "minimum" to condition.minimum,
        ).filterValues { it != null }
        is ItemCondition -> linkedMapOf<String, Any?>(
            "type" to "has item", "material" to condition.material, "amount" to condition.amount,
            "name" to condition.name, "name_contains" to condition.nameContains,
            "lore_contains" to condition.loreContains, "name_ignorecase" to condition.ignoreCase,
        ).filterValues { it != null }
        is ExperienceCondition -> mapOf("type" to "has exp", "amount" to condition.amount, "level" to condition.level)
        is NearCondition -> mapOf("type" to "is near", "location" to condition.location, "distance" to condition.distance)
        is RegexCondition -> mapOf("type" to "regex matches", "input" to condition.input, "regex" to condition.regex)
        is LengthCondition -> mapOf(
            "type" to "string length", "input" to condition.input, "min" to condition.min, "max" to condition.max,
        )
        is TypeCondition -> mapOf("type" to "is object", "input" to condition.input, "object" to condition.type.name)
        is ValueCondition -> describeValue(condition)
        is EnabledCondition -> mapOf("type" to "flag", "name" to condition.flag, "equals" to condition.enabled)
        is ChanceCondition -> mapOf("type" to "chance", "probability" to condition.probability)
        is NotCondition -> condition.check?.let(::describe)?.let { inner ->
            val type = inner["type"].toString()
            inner + ("type" to if (type.startsWith("!")) type.removePrefix("!") else "!$type")
        }
        else -> null
    }

    private fun describeValue(condition: ValueCondition): Map<String, Any?> {
        val symbol = comparators.entries.firstOrNull { it.value == condition.operator }?.key
        if (symbol != null) {
            return mapOf("type" to symbol, "input" to condition.source, "output" to condition.value)
        }
        val named = when {
            condition.operator == Comparison.EQUALS && condition.ignoreCase -> "string equals ignorecase"
            condition.operator == Comparison.EQUALS -> "string equals"
            condition.operator == Comparison.CONTAINS && !condition.ignoreCase -> "string contains"
            else -> null
        }
        if (named != null) return mapOf("type" to named, "input" to condition.source, "output" to condition.value)
        return mapOf(
            "type" to "compare", "source" to condition.source, "operator" to condition.operator.name,
            "value" to condition.value, "ignore_case" to condition.ignoreCase,
        )
    }

    private val typeNames = listOf(
        "has permission", "has permissions", "has item", "has exp", "is near", "string equals",
        "string equals ignorecase", "string contains", "string length", "is object", "regex matches",
    ) + comparators.keys

    private fun allowedKeys(type: String): Set<String>? = when (type) {
        "has permission", "permission", "perm" -> setOf("permission", "node")
        "has permissions", "permissions" -> setOf("permissions", "minimum")
        "has item", "item" ->
            setOf("material", "amount", "name", "name_contains", "lore_contains", "name_ignorecase", "lore_ignorecase")
        "has exp", "exp" -> setOf("amount", "level")
        "is near", "near" -> setOf("location", "distance")
        "string equals", "string equals ignorecase", "string contains" -> setOf("input", "output")
        "string length", "length" -> setOf("input", "min", "max")
        "is object" -> setOf("input", "object")
        "regex matches", "regex" -> setOf("input", "regex")
        "compare", "value" -> setOf("source", "input", "operator", "value", "output", "ignore_case")
        "flag", "enabled" -> setOf("name", "equals")
        "chance" -> setOf("probability")
        "not" -> setOf("check")
        in comparators -> setOf("input", "output")
        else -> null
    }

    /** Rejects a key the [type] does not use, so a typo such as `permision` is not silently ignored. */
    private fun checkKeys(type: String, keys: Set<String>, alsoValid: Set<String>) {
        val allowed = allowedKeys(type) ?: return
        val valid = allowed + "type" + alsoValid
        val known = valid.map(Fields::normalize).toSet()
        for (key in keys) {
            if (Fields.normalize(key) in known) continue
            throw IllegalArgumentException(
                "Unknown key '$key' for type '$type'.${Suggestions.hint(key, valid)} " +
                    "Valid keys: ${(allowed + alsoValid).joinToString()}.",
            )
        }
    }

    private fun normalizeType(type: String): String {
        val lower = type.lowercase(Locale.ROOT)
        return if (lower in comparators) lower else lower.replace('_', ' ').replace('-', ' ').trim().replace(Regex("\\s+"), " ")
    }

    private fun build(type: String, fields: Fields): ActionCondition = when (type) {
        "has permission", "permission", "perm" ->
            PermissionCondition(fields.required("permission", "node"))
        "has permissions", "permissions" ->
            PermissionsCondition(fields.list("permissions"), fields.int("minimum"))
        "has item", "item" -> ItemCondition(
            material = fields.required("material"),
            amount = fields.int("amount") ?: 1,
            name = fields.text("name"),
            nameContains = fields.text("name_contains"),
            loreContains = fields.text("lore_contains"),
            ignoreCase = fields.bool("name_ignorecase") ?: fields.bool("lore_ignorecase") ?: false,
        )
        "has exp", "exp" -> ExperienceCondition(fields.int("amount") ?: 0, fields.bool("level") ?: false)
        "is near", "near" -> NearCondition(fields.required("location"), fields.double("distance") ?: 5.0)
        "string equals" -> ValueCondition(fields.input(), Comparison.EQUALS, fields.output(), ignoreCase = false)
        "string equals ignorecase" -> ValueCondition(fields.input(), Comparison.EQUALS, fields.output(), ignoreCase = true)
        "string contains" -> ValueCondition(fields.input(), Comparison.CONTAINS, fields.output(), ignoreCase = false)
        "string length", "length" -> LengthCondition(
            fields.input(), fields.int("min") ?: 0, fields.int("max") ?: Int.MAX_VALUE,
        )
        "is object" -> TypeCondition(fields.input(), valueType(fields.required("object")))
        "regex matches", "regex" -> RegexCondition(fields.input(), fields.required("regex"))
        "compare", "value" -> ValueCondition(
            source = fields.required("source", "input"),
            operator = comparison(fields.text("operator") ?: "EQUALS"),
            value = fields.text("value", "output") ?: "",
            ignoreCase = fields.bool("ignore_case") ?: true,
        )
        "flag", "enabled" -> EnabledCondition(fields.required("name"), fields.bool("equals") ?: true)
        "chance" -> ChanceCondition(fields.double("probability") ?: 1.0)
        "not" -> NotCondition(fields.condition("check"))
        in comparators -> ValueCondition(fields.input(), comparators.getValue(type), fields.output())
        else -> throw IllegalArgumentException(
            "Unknown condition type '$type'.${Suggestions.hint(type, typeNames)} Types: ${typeNames.joinToString()}.",
        )
    }

    private fun valueType(name: String): ValueType =
        ValueType.values().firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown object type '$name'. Use INT, DOUBLE or UUID")

    private fun comparison(name: String): Comparison {
        val wanted = name.uppercase(Locale.ROOT).replace('-', '_').replace(' ', '_')
        return Comparison.values().firstOrNull { it.name == wanted }
            ?: throw IllegalArgumentException("Unknown comparison '$name'")
    }

    /** Reads the keys of one object ignoring case, `_`, `-` and spaces in their spelling. */
    private class Fields(source: Map<String, Any?>) {
        private val byKey = source.entries.associate { normalize(it.key) to it.value }

        fun raw(vararg keys: String): Any? = keys.firstNotNullOfOrNull { byKey[normalize(it)] }

        fun text(vararg keys: String): String? = raw(*keys)?.toString()

        fun required(vararg keys: String): String =
            text(*keys) ?: throw IllegalArgumentException("Missing '${keys.first()}'")

        fun input(): String = required("input")

        fun output(): String = text("output") ?: ""

        fun int(key: String): Int? = raw(key)?.let {
            (it as? Number)?.toInt() ?: it.toString().trim().toIntOrNull()
                ?: throw IllegalArgumentException("'$key' must be a whole number but was '$it'")
        }

        fun double(key: String): Double? = raw(key)?.let {
            (it as? Number)?.toDouble() ?: it.toString().trim().toDoubleOrNull()
                ?: throw IllegalArgumentException("'$key' must be a number but was '$it'")
        }

        fun bool(key: String): Boolean? = raw(key)?.let {
            it as? Boolean ?: it.toString().trim().lowercase(Locale.ROOT).let { text ->
                when (text) {
                    "true", "yes", "on" -> true
                    "false", "no", "off" -> false
                    else -> throw IllegalArgumentException("'$key' must be true or false but was '$it'")
                }
            }
        }

        fun list(key: String): List<String> = when (val value = raw(key)) {
            null -> emptyList()
            is Iterable<*> -> value.map { it.toString() }
            else -> listOf(value.toString())
        }

        fun condition(key: String): ActionCondition? = raw(key)?.let { value ->
            require(value is Map<*, *>) { "'$key' must be a condition object with a 'type'" }
            parse(value.entries.associate { entry -> entry.key.toString() to entry.value })
        }

        companion object {
            fun normalize(key: String): String =
                key.lowercase(Locale.ROOT).filter { it != '_' && it != '-' && it != ' ' }
        }
    }
}
