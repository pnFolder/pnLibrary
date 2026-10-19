package org.pnfolder.pnlibrary.api.config

/**
 * Numbered changes that bring a configuration file from one version to the next.
 *
 * ```kotlin
 * object ShopSteps : ConfigSteps<ShopConfig>() {
 *     init {
 *         version(2) {
 *             rename("pool-size", "db.pool.size")
 *         }
 *         version(3) {
 *             val address = get("redis-address")
 *             put("redis.host", address.before(":"))
 *             put("redis.port", address.after(":").toInt())
 *             remove("redis-address")
 *         }
 *     }
 * }
 * ```
 *
 * Version numbers start at 2 and must be consecutive; version 1 is the state before any steps.
 * Add new versions at the end and never edit an existing one, because files store the number of
 * the last version applied to them.
 */
open class ConfigSteps<T : Any> {
    private val groups = mutableListOf<Group>()

    /** Version written by this code: the highest declared number, or 1 when there are no steps. */
    val latestVersion: Int get() = groups.maxOfOrNull { it.number } ?: BASE_VERSION

    /** Declares the changes that bring a file to version [number]. */
    protected fun version(number: Int, changes: ConfigChanges.() -> Unit) {
        groups += Group(number, changes)
    }

    /**
     * Applies every declared version above [fromVersion] to [document], in order, and stores the
     * reached version under [VERSION_KEY]. Returns the applied version numbers.
     *
     * @throws IllegalStateException when the numbering is broken or a step fails
     */
    fun applyTo(document: ConfigDocument, fromVersion: Int): List<Int> {
        val pending = plan().filter { it.number > fromVersion }
        pending.forEach { group ->
            try {
                ConfigChanges(document).apply(group.changes)
            } catch (error: Exception) {
                throw IllegalStateException("Config version ${group.number} failed: ${error.message}", error)
            }
            document.set(VERSION_KEY, group.number)
        }
        return pending.map { it.number }
    }

    private fun plan(): List<Group> {
        val sorted = groups.sortedBy { it.number }
        sorted.forEachIndexed { index, group ->
            val expected = BASE_VERSION + 1 + index
            require(group.number == expected) {
                "Config versions must be numbered 2, 3, 4... without gaps or repeats; found ${group.number} where $expected was expected"
            }
        }
        return sorted
    }

    private class Group(val number: Int, val changes: ConfigChanges.() -> Unit)

    /** Constants for the version marker and the empty step set. */
    companion object {
        /** Key that stores the last applied version in a configuration file. */
        const val VERSION_KEY = "_config-version"

        /** Version of a file that has no steps applied, including files without the marker. */
        const val BASE_VERSION = 1

        /** Steps for a configuration that has never changed format. */
        @JvmField
        val NONE: ConfigSteps<Any> = ConfigSteps()
    }
}

/** Operations available inside one `version(n) { ... }` block. */
class ConfigChanges internal constructor(private val document: ConfigDocument) {

    /** Returns the value at [path]. The path must exist. */
    fun get(path: String): Value {
        require(document.contains(path)) { "No value at '$path'" }
        return Value(document.get(path))
    }

    /** Writes [value] at [path], creating missing parent sections. Accepts [Value] results directly. */
    fun put(path: String, value: Any?) {
        document.set(path, if (value is Value) value.unwrap() else value)
    }

    /** Removes the key at [path]. Missing keys are ignored. */
    fun remove(path: String) {
        if (document.contains(path)) document.remove(path)
    }

    /** Moves the value from [from] to [to], removing the old key. The source must exist. */
    fun rename(from: String, to: String) {
        document.move(from, to)
    }
}

/** A value read from the file, with small helpers for the string and number changes migrations need. */
class Value internal constructor(private val raw: Any?) {

    /** Text before the first [separator]; the whole text when the separator is absent. */
    fun before(separator: String): Value = Value(text().substringBefore(separator))

    /** Text after the first [separator]; the whole text when the separator is absent. */
    fun after(separator: String): Value = Value(text().substringAfter(separator))

    /** Whole number from the text. Fails with a readable message for anything else. */
    fun toInt(): Int = text().trim().toIntOrNull() ?: error("'${text()}' is not a whole number")

    internal fun unwrap(): Any? = raw

    private fun text(): String = raw?.toString() ?: error("The value is empty")
}
