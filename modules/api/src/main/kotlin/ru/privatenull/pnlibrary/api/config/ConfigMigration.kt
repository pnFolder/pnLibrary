package ru.privatenull.pnlibrary.api.config

import java.util.ArrayDeque
import java.util.Collections
import java.util.function.UnaryOperator

/** One transformation applied to an untyped configuration document during a version upgrade. */
fun interface ConfigMigration {
    /** Mutates [document] from the step's source schema toward its target schema. */
    fun migrate(document: ConfigDocument)
}

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

/**
 * Directed migration graph for one configuration file.
 *
 * [path] finds the shortest available route from the file's version to [currentVersion]. Steps are
 * returned in execution order. Version identifiers are numeric dotted versions with an optional
 * prerelease suffix, such as `2`, `2.1`, or `3.0-rc.1`.
 *
 * ```kotlin
 * val migrations = ConfigMigrationPlan.builder("2.0")
 *     .assumeVersionWhenMissing("1.0")
 *     .migrate("1.0", "2.0") { document ->
 *         document.move("database.host", "storage.host")
 *     }
 *     .build()
 * ```
 */
class ConfigMigrationPlan private constructor(
    /** Schema version written after a successful migration. */
    val currentVersion: String,
    /** Version assigned to documents without [versionKey], or `null` to reject them. */
    val assumedVersion: String?,
    /** Root YAML key that stores the schema version. */
    val versionKey: String,
    /** Immutable directed edges available to the migration engine. */
    val steps: List<Step>,
) {
    /**
     * One directed migration edge.
     *
     * @property from source schema version accepted by this edge
     * @property to target schema version produced by this edge
     * @property migration document transformation executed for the edge
     */
    data class Step(val from: String, val to: String, val migration: ConfigMigration)

    /** Returns the shortest ordered migration route from [from] to [currentVersion]. */
    fun path(from: String): List<Step> {
        if (from == currentVersion) return emptyList()
        val queue = ArrayDeque<Pair<String, List<Step>>>()
        val visited = mutableSetOf(from)
        queue.add(from to emptyList())
        while (queue.isNotEmpty()) {
            val (version, route) = queue.removeFirst()
            steps.filter { it.from == version }.forEach { step ->
                val next = route + step
                if (step.to == currentVersion) return next
                if (visited.add(step.to)) queue.add(step.to to next)
            }
        }
        throw IllegalStateException("No configuration migration path from $from to $currentVersion")
    }

    /** Entry point for constructing validated migration graphs. */
    companion object {
        /** Creates a migration-plan builder targeting [currentVersion]. */
        @JvmStatic
        fun builder(currentVersion: String) = Builder(currentVersion)
    }

    /** Java-friendly builder for a directed configuration migration graph. */
    class Builder(private val currentVersion: String) {
        private var assumedVersion: String? = null
        private var versionKey = "_config-version"
        private val steps = mutableListOf<Step>()
        /** Assigns [version] to existing files that do not contain the version key. */
        fun assumeVersionWhenMissing(version: String) = apply { assumedVersion = checked(version) }

        /** Changes the root key used to read and write the schema version. */
        fun versionKey(path: String) = apply {
            versionKey = path.also { require(it.isNotBlank() && '.' !in it) { "Version key must be one YAML key" } }
        }
        /** Adds one unique directed migration step. */
        fun migrate(from: String, to: String, migration: ConfigMigration) = apply {
            val step = Step(checked(from), checked(to), migration)
            require(step.from != step.to) { "Migration versions must differ" }
            require(steps.none { it.from == step.from && it.to == step.to }) {
                "Duplicate migration ${step.from} -> ${step.to}"
            }
            steps += step
        }
        /** Validates versions and reachability of the assumed version and creates the plan. */
        fun build(): ConfigMigrationPlan {
            val target = checked(currentVersion)
            val plan = ConfigMigrationPlan(
                target,
                assumedVersion,
                versionKey,
                Collections.unmodifiableList(ArrayList(steps)),
            )
            assumedVersion?.let(plan::path)
            return plan
        }
        private fun checked(value: String) = value.trim().also {
            require(it.matches(Regex("[0-9]+(?:\\.[0-9]+)*(?:-[0-9A-Za-z.-]+)?"))) {
                "Invalid config version: $value"
            }
        }
    }
}
