package ru.privatenull.pnlibrary.core.placeholders

import ru.privatenull.pnlibrary.api.placeholders.PlaceholderValueScope
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderValueStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.UnaryOperator

/** Thread-safe in-memory implementation of the runtime placeholder value store. */
internal class GlobalPlaceholderValueStore : PlaceholderValueStore {
    private val definitions = ConcurrentHashMap<String, Definition>()

    override fun create(parameter: String, scope: PlaceholderValueScope, defaultValue: String?): Boolean {
        val key = key(parameter)
        return definitions.putIfAbsent(key, Definition(scope, defaultValue)) == null
    }

    override fun get(parameter: String, playerId: UUID?): String? {
        val definition = definitions[key(parameter)] ?: return null
        return definition.values[subject(definition, playerId)] ?: definition.defaultValue
    }

    override fun set(parameter: String, value: String, playerId: UUID?): String {
        val definition = required(parameter)
        definition.values[subject(definition, playerId)] = value
        return value
    }

    override fun update(parameter: String, playerId: UUID?, operator: UnaryOperator<String>): String {
        val definition = required(parameter)
        val subject = subject(definition, playerId)
        return definition.values.compute(subject) { _, current ->
            operator.apply(current ?: definition.defaultValue.orEmpty())
        } ?: ""
    }

    override fun increment(parameter: String, amount: Long, playerId: UUID?): String =
        update(parameter, playerId) { current -> Math.addExact(current.toLongOrNull() ?: 0, amount).toString() }

    override fun decrement(parameter: String, amount: Long, playerId: UUID?): String =
        increment(parameter, -amount, playerId)

    override fun remove(parameter: String, playerId: UUID?): Boolean {
        val definition = definitions[key(parameter)] ?: return false
        return definition.values.remove(subject(definition, playerId)) != null
    }

    override fun contains(parameter: String): Boolean = definitions.containsKey(key(parameter))

    override fun parameters(): Set<String> = definitions.keys.toSortedSet()

    private fun required(parameter: String): Definition = definitions[key(parameter)]
        ?: throw IllegalArgumentException("Unknown placeholder value parameter: $parameter")

    private fun subject(definition: Definition, playerId: UUID?): String = when (definition.scope) {
        PlaceholderValueScope.GLOBAL -> GLOBAL_SUBJECT
        PlaceholderValueScope.PLAYER -> playerId?.toString()
            ?: throw IllegalArgumentException("Player-scoped parameter requires a player UUID")
    }

    private fun key(parameter: String): String {
        val normalized = parameter.trim().lowercase(java.util.Locale.ROOT)
        require(normalized.matches(PARAMETER)) { "Invalid placeholder value parameter: $parameter" }
        return normalized
    }

    private class Definition(val scope: PlaceholderValueScope, val defaultValue: String?) {
        val values = ConcurrentHashMap<String, String>()
    }

    private companion object {
        const val GLOBAL_SUBJECT = "global"
        val PARAMETER = Regex("[a-z0-9_.-]+")
    }
}
