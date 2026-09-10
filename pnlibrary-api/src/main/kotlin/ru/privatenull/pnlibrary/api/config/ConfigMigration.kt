package ru.privatenull.pnlibrary.api.config

import java.util.ArrayDeque
import java.util.function.UnaryOperator

/** One atomic transformation of an untyped configuration document. */
fun interface ConfigMigration {
    fun migrate(document: ConfigDocument)
}

/** Mutable YAML-like document with safe dot-separated path operations. */
class ConfigDocument(source: Map<String, Any?>) {
    private val root: MutableMap<String, Any?> = mutableMap(source)

    fun contains(path: String): Boolean = lookup(path).first
    fun get(path: String): Any? = lookup(path).second
    fun set(path: String, value: Any?): ConfigDocument = apply {
        val parts = parts(path)
        val parent = parent(parts, create = true)
        parent[parts.last()] = mutableValue(value)
    }
    fun remove(path: String): Any? {
        val parts = parts(path)
        return parent(parts, create = false).remove(parts.last())
    }
    fun move(from: String, to: String): ConfigDocument = apply {
        require(contains(from)) { "Migration source path does not exist: $from" }
        val value = remove(from)
        set(to, value)
    }
    fun rename(path: String, newName: String): ConfigDocument = apply {
        require('.' !in newName && newName.isNotBlank()) { "New key must be one non-empty path component" }
        val parent = path.substringBeforeLast('.', "")
        move(path, if (parent.isEmpty()) newName else "$parent.$newName")
    }
    fun transform(path: String, transformer: UnaryOperator<Any?>): ConfigDocument = apply {
        require(contains(path)) { "Migration path does not exist: $path" }
        set(path, transformer.apply(get(path)))
    }
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
        is Map<*, *> -> linkedMapOf<String, Any?>().also { out -> value.forEach { (k, v) -> out[k.toString()] = mutableValue(v) } }
        is List<*> -> value.map(::mutableValue).toMutableList()
        else -> value
    }
    private fun immutableMap(value: Map<String, Any?>): Map<String, Any?> =
        linkedMapOf<String, Any?>().also { out -> value.forEach { (k, v) -> out[k] = immutableValue(v) } }
    private fun immutableValue(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries.associateTo(linkedMapOf()) { it.key.toString() to immutableValue(it.value) }
        is List<*> -> value.map(::immutableValue)
        else -> value
    }
}

/** Directed migration graph for one configuration file. */
class ConfigMigrationPlan private constructor(
    val currentVersion: String,
    val assumedVersion: String?,
    val versionKey: String,
    val steps: List<Step>,
) {
    data class Step(val from: String, val to: String, val migration: ConfigMigration)

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

    companion object { @JvmStatic fun builder(currentVersion: String) = Builder(currentVersion) }

    class Builder(private val currentVersion: String) {
        private var assumedVersion: String? = null
        private var versionKey = "_config-version"
        private val steps = mutableListOf<Step>()
        fun assumeVersionWhenMissing(version: String) = apply { assumedVersion = checked(version) }
        fun versionKey(path: String) = apply {
            versionKey = path.also { require(it.isNotBlank() && '.' !in it) { "Version key must be one YAML key" } }
        }
        fun migrate(from: String, to: String, migration: ConfigMigration) = apply {
            val step = Step(checked(from), checked(to), migration)
            require(step.from != step.to) { "Migration versions must differ" }
            require(steps.none { it.from == step.from && it.to == step.to }) { "Duplicate migration ${step.from} -> ${step.to}" }
            steps += step
        }
        fun build(): ConfigMigrationPlan {
            val target = checked(currentVersion)
            val plan = ConfigMigrationPlan(target, assumedVersion, versionKey, steps.toList())
            assumedVersion?.let(plan::path)
            return plan
        }
        private fun checked(value: String) = value.trim().also { require(it.matches(Regex("[0-9]+(?:\\.[0-9]+)*(?:-[0-9A-Za-z.-]+)?"))) { "Invalid config version: $value" } }
    }
}
