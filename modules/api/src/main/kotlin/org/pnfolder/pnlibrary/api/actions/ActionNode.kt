package org.pnfolder.pnlibrary.api.actions

import org.pnfolder.pnlibrary.api.config.ConfigNodeRewriter

/**
 * Reads the action forms of the configuration style: every node has a `type`, and wrappers hold their
 * actions under `do`.
 *
 * | Written | Read as |
 * |---|---|
 * | `type: delay`, `duration`, `do` | [DelayAction] |
 * | `type: chance`, `probability: 10%`, `do`, `else` | [ConditionalAction] with a [ChanceCondition] |
 * | `type: if`, `check`, `do`, `else` | [ConditionalAction] |
 * | `type: repeat`, `times`, `every`, `do` | [RepeatAction] |
 * | `type: group`, `do` | [SequenceAction] |
 * | `type: message`, `message` | [MessageAction] with one line |
 */
object ActionNode : ConfigNodeRewriter {
    private val messageTypes = setOf("message", "messages", "msg")
    private val doToThen = setOf("delay", "later", "repeat")
    private val conditionTypes = setOf("if", "when", "condition")
    private val groupTypes = setOf("group", "sequence")

    override fun rewrite(node: Map<String, Any?>, path: String): Map<String, Any?> {
        val key = node.keys.firstOrNull { it.equals("type", ignoreCase = true) } ?: return node
        val type = node[key]?.toString()?.trim()?.lowercase()?.replace('_', '-') ?: return node
        return when (type) {
            in messageTypes -> lines(renamed(node, "message", "messages"))
            in doToThen -> renamed(node, "do", "then")
            in groupTypes -> if ("do" in node) renamed(node, "do", "steps") else node
            "chance" -> chance(node)
            in conditionTypes -> condition(node, key)
            else -> node
        }
    }

    /** A message written as one text stands for a list of one line. */
    private fun lines(node: Map<String, Any?>): Map<String, Any?> {
        val value = node["messages"]
        return if (value == null || value is List<*>) node else node + ("messages" to listOf(value))
    }

    private fun renamed(node: Map<String, Any?>, from: String, to: String): Map<String, Any?> {
        if (from !in node) return node
        require(to !in node) { "Use either '$from' or '$to', not both" }
        return node.entries.associate { (if (it.key == from) to else it.key) to it.value }
    }

    private fun chance(node: Map<String, Any?>): Map<String, Any?> {
        val probability = percent(node["probability"] ?: node["chance"] ?: node["value"]
            ?: throw IllegalArgumentException("A chance needs a 'probability' such as 10%"))
        val out = linkedMapOf<String, Any?>("type" to "if")
        out["all"] = listOf(mapOf("type" to "chance", "probability" to probability))
        node["do"]?.let { out["then"] = it }
        node["then"]?.let { out["then"] = it }
        node["else"]?.let { out["else"] = it }
        return out
    }

    private fun condition(node: Map<String, Any?>, typeKey: String): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        node.forEach { (key, value) ->
            when (key) {
                typeKey -> out["type"] = "if"
                "do" -> out["then"] = value
                "check" -> out["all"] = if (value is List<*>) value else listOf(value)
                else -> out[key] = value
            }
        }
        return out
    }

    private fun percent(raw: Any): Double {
        val text = raw.toString().trim()
        val number = if (text.endsWith("%")) text.dropLast(1).trim().toDoubleOrNull()?.div(100) else text.toDoubleOrNull()
        require(number != null && number in 0.0..1.0) { "Chance '$text' must be a percentage from 0% to 100%" }
        return number
    }
}
