package org.pnfolder.pnlibrary.api.config

import java.util.Collections
import java.util.function.UnaryOperator

/**
 * Mutable YAML-like document exposed to [ConfigMigration] implementations.
 *
 * Paths use dot-separated map keys, for example `database.pool.maximum`. Lists are values and
 * cannot be traversed by index. The constructor and [toMap] defensively copy nested maps and lists,
 * so migration code cannot mutate the parser's source data or the returned snapshot indirectly.
 */
class ConfigDocument(source: Map<String, Any?>) {
    private val root: MutableMap<String, Any?> = mutableMap(source)

    /** Returns whether [path] exists, including when its value is `null`. */
    fun contains(path: String): Boolean = lookup(path).first

    /** Returns the value at [path], or `null` when absent or explicitly null. */
    fun get(path: String): Any? = lookup(path).second

    /** Creates missing parent maps and stores a defensive copy of [value] at [path]. */
    fun set(path: String, value: Any?): ConfigDocument = apply {
        val parts = parts(path)
        val parent = parent(parts, create = true)
        parent[parts.last()] = mutableValue(value)
    }

    /** Removes and returns the value at [path], or `null` when the path is absent. */
    fun remove(path: String): Any? {
        val parts = parts(path)
        return parent(parts, create = false).remove(parts.last())
    }

    /** Moves an existing value from [from] to [to], replacing the destination when necessary. */
    fun move(from: String, to: String): ConfigDocument = apply {
        require(contains(from)) { "Migration source path does not exist: $from" }
        val value = remove(from)
        set(to, value)
    }

    /** Renames the final key component of [path] without moving it to another parent. */
    fun rename(path: String, newName: String): ConfigDocument = apply {
        require('.' !in newName && newName.isNotBlank()) { "New key must be one non-empty path component" }
        val parent = path.substringBeforeLast('.', "")
        move(path, if (parent.isEmpty()) newName else "$parent.$newName")
    }

    /** Replaces an existing value with the result returned by [transformer]. */
    fun transform(path: String, transformer: UnaryOperator<Any?>): ConfigDocument = apply {
        require(contains(path)) { "Migration path does not exist: $path" }
        set(path, transformer.apply(get(path)))
    }

    /** Returns an immutable deep snapshot of this document. */
    fun toMap(): Map<String, Any?> = immutableMap(root)

    private fun lookup(path: String): Pair<Boolean, Any?> {
        val parts = parts(path)
        var current: Any? = root
        parts.forEach { part ->
            val map = current as? Map<*, *> ?: return false to null
            if (!map.containsKey(part)) return false to null
            current = map[part]
        }
        return true to current
    }

    @Suppress("UNCHECKED_CAST")
    private fun parent(parts: List<String>, create: Boolean): MutableMap<String, Any?> {
        var current = root
        parts.dropLast(1).forEach { part ->
            val next = current[part]
            current = when (next) {
                is MutableMap<*, *> -> next as MutableMap<String, Any?>
                is Map<*, *> -> mutableMap(next as Map<String, Any?>).also { current[part] = it }
                null -> if (create) linkedMapOf<String, Any?>().also { current[part] = it }
                    else return linkedMapOf()
                else -> throw IllegalArgumentException("Path component is not an object: $part")
            }
        }
        return current
    }

    private fun parts(path: String) = path.split('.').also {
        require(it.isNotEmpty() && it.none(String::isBlank)) { "Invalid configuration path: $path" }
    }

    private fun mutableMap(value: Map<String, Any?>): MutableMap<String, Any?> =
        linkedMapOf<String, Any?>().also { out -> value.forEach { (k, v) -> out[k] = mutableValue(v) } }

    private fun mutableValue(value: Any?): Any? = when (value) {
        is Map<*, *> -> linkedMapOf<String, Any?>().also { out ->
            value.forEach { (key, nestedValue) ->
                out[key.toString()] = mutableValue(nestedValue)
            }
        }
        is List<*> -> value.map(::mutableValue).toMutableList()
        else -> value
    }

    private fun immutableMap(value: Map<String, Any?>): Map<String, Any?> =
        Collections.unmodifiableMap(
            linkedMapOf<String, Any?>().also { out -> value.forEach { (k, v) -> out[k] = immutableValue(v) } },
        )

    private fun immutableValue(value: Any?): Any? = when (value) {
        is Map<*, *> -> Collections.unmodifiableMap(
            value.entries.associateTo(linkedMapOf()) { it.key.toString() to immutableValue(it.value) },
        )
        is List<*> -> Collections.unmodifiableList(value.map(::immutableValue))
        else -> value
    }
}
