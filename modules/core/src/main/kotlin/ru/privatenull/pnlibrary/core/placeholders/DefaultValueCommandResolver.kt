package ru.privatenull.pnlibrary.core.placeholders

import ru.privatenull.pnlibrary.api.placeholders.PlaceholderValueScope
import java.util.UUID

/** Parses the bracket-delimited `defaultValue_*` command surface published to PlaceholderAPI. */
internal class DefaultValueCommandResolver(private val store: GlobalPlaceholderValueStore) {
    fun resolve(command: String, playerId: UUID?): String? {
        val operation = command.substringBefore('_').lowercase()
        val arguments = command.substringAfter('_', "")
        val parsed = parseArguments(arguments) ?: return null
        return when (operation) {
            "create" -> parsed.single()?.let { store.create(it, PlaceholderValueScope.PLAYER).toString() }
            "createglobal" -> parsed.single()?.let { store.create(it, PlaceholderValueScope.GLOBAL).toString() }
            "get" -> parsed.single()?.let { store.get(it, playerId) }
            "exists" -> parsed.single()?.let { store.contains(it).toString() }
            "remove" -> parsed.single()?.let { store.remove(it, playerId).toString() }
            "set" -> parsed.pair()?.let { (parameter, value) -> store.set(parameter, value, playerId) }
            "increment" -> parsed.pair()?.let { (parameter, value) -> store.increment(parameter, value.toLong(), playerId) }
            "decrement" -> parsed.pair()?.let { (parameter, value) -> store.decrement(parameter, value.toLong(), playerId) }
            else -> null
        }
    }

    private fun parseArguments(arguments: String): List<String>? {
        if (arguments.isEmpty()) return null
        val values = mutableListOf<String>()
        var offset = 0
        while (offset < arguments.length) {
            if (arguments[offset] != '[') return null
            val closingBracket = arguments.indexOf(']', offset + 1)
            if (closingBracket < 0) return null
            values += arguments.substring(offset + 1, closingBracket)
            offset = closingBracket + 1
            if (offset < arguments.length) {
                if (arguments[offset] != '_') return null
                offset++
            }
        }
        return values.takeIf { it.none(String::isEmpty) }
    }

    private fun List<String>.single(): String? = takeIf { size == 1 }?.first()

    private fun List<String>.pair(): Pair<String, String>? = takeIf { size == 2 }?.let { it[0] to it[1] }
}
