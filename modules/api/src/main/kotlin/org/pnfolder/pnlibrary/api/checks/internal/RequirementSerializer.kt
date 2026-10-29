package org.pnfolder.pnlibrary.api.checks.internal

import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.checks.Requirement
import org.pnfolder.pnlibrary.api.config.ConfigSerializationContext
import org.pnfolder.pnlibrary.api.config.ConfigSerializer
import org.pnfolder.pnlibrary.api.internal.InternalPnLibraryApi
import org.pnfolder.pnlibrary.api.internal.Suggestions

/**
 * Reads and writes a [Requirement].
 *
 * - Short: `check:` with one check, plus `deny` and `success`.
 * - DeluxeMenus: `requirements:` with named entries, `minimum_requirements`, `stop_at_success`,
 *   `deny_commands` and `success_commands`.
 * - One entry: the fields of a check (`type`, …) or `if: "%x% >= 5"`, plus `optional`, `deny` and `success`.
 * - Text: `"%vault_eco_balance% >= 100"`, a check with nothing to run.
 *
 * `deny` and `success` hold actions written with a `type`. `deny_commands` and `success_commands` are their
 * DeluxeMenus names.
 */
@InternalPnLibraryApi
object RequirementSerializer : ConfigSerializer<Requirement> {
    private val shortKeys = listOf("check", "deny", "success", "deny_commands", "success_commands")
    private val groupKeys = listOf(
        "requirements", "minimum_requirements", "stop_at_success", "deny", "success", "deny_commands", "success_commands",
    )
    private val entryKeys = listOf("optional", "deny_commands", "success_commands", "deny", "success", "if")

    /** Reads a [Requirement] from a single expression or an object with `checks`. */
    override fun deserialize(value: Any?, context: ConfigSerializationContext): Requirement {
        if (value is String) return Requirement(CheckExpression.parse(value))
        require(value is Map<*, *>) {
            "A check must be an object with 'check:', 'requirements:' or a 'type', or a text such as '%x% >= 5'"
        }
        val fields = value.entries.associate { it.key.toString() to it.value }
        val lower = fields.mapKeys { it.key.lowercase() }
        return when {
            "requirements" in lower -> group(fields, lower, context)
            "check" in lower -> short(fields, lower, context)
            else -> Requirement(mapOf("check" to entry(fields, context, context.path)))
        }
    }

    private fun short(fields: Map<String, Any?>, lower: Map<String, Any?>, context: ConfigSerializationContext): Requirement {
        unknownKeys(fields.keys, shortKeys, "check")
        return Requirement(
            CheckSerializer.read(lower["check"], "check"),
            deny = actions(lower, "deny_commands", "deny", context),
            success = actions(lower, "success_commands", "success", context),
        )
    }

    private fun group(fields: Map<String, Any?>, lower: Map<String, Any?>, context: ConfigSerializationContext): Requirement {
        unknownKeys(fields.keys, groupKeys, "requirements")
        val raw = lower["requirements"]
        require(raw is Map<*, *>) { "'requirements' must hold named checks" }
        val entries = LinkedHashMap<String, Requirement.Entry>()
        raw.forEach { (name, item) ->
            val path = "${context.path}.requirements.$name"
            entries[name.toString()] = when (item) {
                is String -> Requirement.Entry(CheckExpression.parse(item))
                is Map<*, *> -> if (context.decoder != null) {
                    context.decode(item, Requirement.Entry::class.java, path)
                } else {
                    entry(item.entries.associate { it.key.toString() to it.value }, context, path)
                }
                else -> throw IllegalArgumentException("Check '$name' must be an object with a 'type'")
            }
        }
        return Requirement(
            entries = entries,
            minimum = whole(lower["minimum_requirements"], "minimum_requirements") ?: 0,
            stopAtSuccess = bool(lower["stop_at_success"], "stop_at_success") ?: false,
            success = actions(lower, "success_commands", "success", context),
            deny = actions(lower, "deny_commands", "deny", context),
        )
    }

    /** Reads one entry of a `requirements:` block; used through [RequirementEntrySerializer] so errors carry its path. */
    internal fun entry(fields: Map<String, Any?>, context: ConfigSerializationContext, path: String): Requirement.Entry {
        val lower = fields.mapKeys { it.key.lowercase() }
        val expression = lower["if"]
        val check = if (expression != null) {
            require(!lower.containsKey("type")) { "Use either 'if' or 'type', not both" }
            unknownKeys(fields.keys, entryKeys, "if")
            CheckExpression.parse(expression.toString())
        } else {
            CheckSerializer.parse(fields.filterKeys { it.lowercase() !in entryKeys }, entryKeys.toSet())
        }
        val at = context.copy(path = path)
        return Requirement.Entry(
            check = check,
            optional = bool(lower["optional"], "optional") ?: false,
            success = actions(lower, "success_commands", "success", at),
            deny = actions(lower, "deny_commands", "deny", at),
        )
    }

    /** Writes [value] in the shortest configuration form that keeps its meaning. */
    override fun serialize(value: Requirement, context: ConfigSerializationContext): Any? {
        val single = value.entries.entries.singleOrNull()
        if (single != null && single.key == "check" && !single.value.optional && value.minimum == 0 &&
            single.value.deny.isEmpty() && single.value.success.isEmpty()
        ) {
            val out = linkedMapOf<String, Any?>("check" to CheckSerializer.write(checkOf(single.value)))
            if (value.deny.isNotEmpty()) out["deny"] = written(value.deny, context, "deny")
            if (value.success.isNotEmpty()) out["success"] = written(value.success, context, "success")
            return out
        }
        val entries = linkedMapOf<String, Any?>()
        value.entries.forEach { (name, entry) ->
            val written = CheckSerializer.describe(checkOf(entry))
                ?: throw UnsupportedOperationException("Check '$name' cannot be written to configuration")
            val out = LinkedHashMap<String, Any?>(written)
            if (entry.optional) out["optional"] = true
            if (entry.success.isNotEmpty()) out["success"] = written(entry.success, context, "requirements.$name.success")
            if (entry.deny.isNotEmpty()) out["deny"] = written(entry.deny, context, "requirements.$name.deny")
            entries[name] = out
        }
        val out = linkedMapOf<String, Any?>("requirements" to entries)
        if (value.minimum > 0) out["minimum_requirements"] = value.minimum
        if (value.stopAtSuccess) out["stop_at_success"] = true
        if (value.success.isNotEmpty()) out["success"] = written(value.success, context, "success")
        if (value.deny.isNotEmpty()) out["deny"] = written(value.deny, context, "deny")
        return out
    }

    private fun checkOf(entry: Requirement.Entry) =
        entry.check ?: throw UnsupportedOperationException("A check without a type cannot be saved")

    private fun unknownKeys(keys: Set<String>, valid: List<String>, next: String) {
        keys.firstOrNull { it.lowercase() !in valid }?.let { key ->
            throw IllegalArgumentException(
                "Unknown key '$key' next to '$next'.${Suggestions.hint(key, valid)} Valid keys: ${valid.joinToString()}.",
            )
        }
    }

    /** Reads `deny`/`success` (or their DeluxeMenus names) as a list of actions written with a `type`. */
    private fun actions(
        fields: Map<String, Any?>, long: String, short: String, context: ConfigSerializationContext,
    ): List<Action> {
        require(!(fields.containsKey(long) && fields.containsKey(short))) { "Use either '$short' or '$long', not both" }
        val key = if (fields.containsKey(long)) long else short
        return when (val source = fields[key]) {
            null -> emptyList()
            is Iterable<*> -> source.mapIndexed { index, item -> action(item, context, "${context.path}.$key[$index]") }
            else -> listOf(action(source, context, "${context.path}.$key"))
        }
    }

    private fun action(item: Any?, context: ConfigSerializationContext, path: String): Action {
        checkNotNull(context.decoder) { "Actions can only be read while a configuration loads" }
        return context.decode(item, Action::class.java, path)
    }

    private fun written(actions: List<Action>, context: ConfigSerializationContext, key: String): List<Any?> =
        actions.mapIndexed { index, action -> context.encode(action, Action::class.java, "${context.path}.$key[$index]") }

    private fun whole(raw: Any?, key: String): Int? = raw?.let {
        (it as? Number)?.toInt() ?: it.toString().trim().toIntOrNull()
            ?: throw IllegalArgumentException("'$key' must be a whole number but was '$it'")
    }

    private fun bool(raw: Any?, key: String): Boolean? = raw?.let {
        it as? Boolean ?: when (it.toString().trim().lowercase()) {
            "true", "yes", "on" -> true
            "false", "no", "off" -> false
            else -> throw IllegalArgumentException("'$key' must be true or false but was '$it'")
        }
    }
}

/** Reads one entry of a DeluxeMenus `requirements:` block: the fields of a check plus `optional`, `deny` and `success`. */
@InternalPnLibraryApi
object RequirementEntrySerializer : ConfigSerializer<Requirement.Entry> {
    /** Reads one check entry from an expression or an object with a `type`. */
    override fun deserialize(value: Any?, context: ConfigSerializationContext): Requirement.Entry {
        if (value is String) return Requirement.Entry(CheckExpression.parse(value))
        require(value is Map<*, *>) { "A check must be an object with a 'type'" }
        return RequirementSerializer.entry(value.entries.associate { it.key.toString() to it.value }, context, context.path)
    }

    /** Always fails: an entry is written by its [Requirement]. */
    override fun serialize(value: Requirement.Entry, context: ConfigSerializationContext): Any? =
        throw UnsupportedOperationException("A requirement entry is written by its requirement")
}
