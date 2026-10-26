package org.pnfolder.pnlibrary.api.actions.internal

import org.pnfolder.pnlibrary.api.config.ConfigNodeRewriter

/**
 * Turns the friendly action objects into the fields of the action classes before they are read.
 *
 * | Written | Read as |
 * |---|---|
 * | `type: message`, `message: text` | [org.pnfolder.pnlibrary.api.actions.builtin.MessageAction] with one line |
 * | `type: sound`, `sound: entity.villager.no` | [org.pnfolder.pnlibrary.api.actions.builtin.SoundAction]; Bukkit names such as `ENTITY_VILLAGER_NO` work too |
 * | `type: title`, `fade-in`, `stay`, `fade-out` | [org.pnfolder.pnlibrary.api.actions.builtin.TitleAction] |
 * | `type: delay`, `duration`, `do` | [org.pnfolder.pnlibrary.api.actions.builtin.DelayAction]; without `do` it pauses the rest of the list |
 * | `type: repeat`, `times`, `every`, `do` | [org.pnfolder.pnlibrary.api.actions.builtin.RepeatAction] |
 * | `type: group`, `do` | [org.pnfolder.pnlibrary.api.actions.builtin.GroupAction] |
 * | `type: if`, `check`, `do`, `else` | [org.pnfolder.pnlibrary.api.actions.builtin.IfAction]; a list under `check` means all of them |
 * | `type: chance`, `probability: 10%`, `do`, `else` | [org.pnfolder.pnlibrary.api.actions.builtin.IfAction] with a chance check |
 * | `type: console` or `type: player`, `command` | [org.pnfolder.pnlibrary.api.actions.builtin.CommandAction] run by the console or the player |
 * | `type: requirements` with the DeluxeMenus block | [org.pnfolder.pnlibrary.api.actions.builtin.RequireAction] |
 *
 * The older `if` form with `all`, `any` and `then` is read as well.
 */
object ActionNode : ConfigNodeRewriter {
    /** Rewrites the older `if` form into the current action node shape. */
    override fun rewrite(node: Map<String, Any?>, path: String): Map<String, Any?> {
        val key = node.keys.firstOrNull { it.equals("type", ignoreCase = true) }
            ?: return if (node.keys.any { it.equals("requirements", ignoreCase = true) }) requirements(node, null) else node
        val type = node[key]?.toString()?.trim()?.lowercase()?.replace('_', '-') ?: return node
        return when (type) {
            "message", "messages", "msg" -> lines(renamed(node, "message", "messages"))
            "sound" -> sound(renamed(node, "sound", "key"))
            "title" -> renamed(renamed(node, "fade-in", "fadeIn"), "fade-out", "fadeOut")
            "delay", "wait", "pause", "later", "repeat" -> renamed(node, "do", "then")
            "group", "sequence" -> renamed(node, "do", "steps")
            "if", "when" -> condition(node, key)
            "chance" -> chance(node, key)
            "console", "player", "commandevent" -> command(node, key, if (type == "console") "console" else "player")
            "requirements" -> requirements(node, key)
            else -> node
        }
    }

    /** A message written as one text stands for a list of one line. */
    private fun lines(node: Map<String, Any?>): Map<String, Any?> {
        val value = node["messages"]
        return if (value == null || value is List<*>) node else node + ("messages" to listOf(value))
    }

    /** Turns `ENTITY_VILLAGER_NO` into `entity.villager.no`; keys with a `:` or `.` stay as written. */
    private fun sound(node: Map<String, Any?>): Map<String, Any?> {
        val key = node["key"]?.toString() ?: return node
        val normalized = if (':' in key || '.' in key) key.lowercase() else key.lowercase().replace('_', '.')
        return node + ("key" to normalized)
    }

    private fun renamed(node: Map<String, Any?>, from: String, to: String): Map<String, Any?> {
        if (from !in node) return node
        require(to !in node) { "Use either '$from' or '$to', not both" }
        return node.entries.associate { (if (it.key == from) to else it.key) to it.value }
    }

    private fun condition(node: Map<String, Any?>, typeKey: String): Map<String, Any?> {
        require(listOf("check", "all", "any").count { it in node } <= 1) { "Use one of 'check', 'all' or 'any'" }
        require(!("do" in node && "then" in node)) { "Use either 'do' or 'then', not both" }
        val out = linkedMapOf<String, Any?>()
        node.forEach { (key, value) ->
            when (key) {
                typeKey -> out["type"] = "if"
                "do" -> out["then"] = value
                "check" -> out["check"] = if (value is List<*>) mapOf("type" to "all", "checks" to value) else value
                "all", "any" -> out["check"] = mapOf("type" to key, "checks" to value)
                else -> out[key] = value
            }
        }
        return out
    }

    private fun chance(node: Map<String, Any?>, typeKey: String): Map<String, Any?> {
        val probability = node["probability"] ?: node["chance"]
            ?: throw IllegalArgumentException("A chance needs a 'probability' such as 10%")
        val out = linkedMapOf<String, Any?>("type" to "if", "check" to mapOf("type" to "chance", "probability" to probability))
        node.forEach { (key, value) ->
            when (key) {
                typeKey, "probability", "chance" -> Unit
                "do", "then" -> out["then"] = value
                "else" -> out["else"] = value
                else -> throw IllegalArgumentException("Unknown key '$key' for a chance. Valid keys: probability, do, else")
            }
        }
        return out
    }

    private fun command(node: Map<String, Any?>, typeKey: String, runner: String): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>("type" to "command", "as" to runner)
        node.forEach { (key, value) -> if (key != typeKey) out[key] = value }
        return out
    }

    private fun requirements(node: Map<String, Any?>, typeKey: String?): Map<String, Any?> =
        linkedMapOf("type" to "require", "requirement" to node.filterKeys { it != typeKey })
}
