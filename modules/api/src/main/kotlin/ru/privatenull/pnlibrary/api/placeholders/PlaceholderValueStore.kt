package ru.privatenull.pnlibrary.api.placeholders

import java.util.UUID
import java.util.function.UnaryOperator

/** Defines whether one stored placeholder value is shared or isolated by player. */
enum class PlaceholderValueScope {
    /** One value shared by the whole pnLibrary runtime. */
    GLOBAL,
    /** A separate value for every player UUID. */
    PLAYER,
}

/**
 * Process-wide string value store exposed through pnLibrary placeholders.
 *
 * Parameters must be registered before use. Names may contain letters, digits,
 * underscores, dots, and hyphens. Implementations are thread-safe.
 */
interface PlaceholderValueStore {
    /** Registers [parameter] and returns `false` when it already exists. */
    fun create(parameter: String, scope: PlaceholderValueScope = PlaceholderValueScope.PLAYER, defaultValue: String? = null): Boolean

    /** Returns the stored or default value, or `null` when the parameter is unknown or empty. */
    fun get(parameter: String, playerId: UUID? = null): String?

    /** Stores [value] and returns it. Player-scoped fields require [playerId]. */
    fun set(parameter: String, value: String, playerId: UUID? = null): String

    /** Atomically transforms the current value and returns the result. */
    fun update(parameter: String, playerId: UUID? = null, operator: UnaryOperator<String>): String

    /** Adds [amount] to a numeric value and returns the result. */
    fun increment(parameter: String, amount: Long = 1, playerId: UUID? = null): String

    /** Subtracts [amount] from a numeric value and returns the result. */
    fun decrement(parameter: String, amount: Long = 1, playerId: UUID? = null): String

    /** Removes the current value without unregistering the parameter. */
    fun remove(parameter: String, playerId: UUID? = null): Boolean

    /** Returns whether [parameter] is registered. */
    fun contains(parameter: String): Boolean

    /** Returns all registered parameter names in sorted order. */
    fun parameters(): Set<String>
}
