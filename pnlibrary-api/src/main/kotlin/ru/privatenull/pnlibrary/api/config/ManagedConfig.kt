package ru.privatenull.pnlibrary.api.config

import java.io.File
import java.util.function.UnaryOperator

/** Converts between a typed code-first model [T] and YAML text. */
interface ConfigCodec<T> {
    /** Serializes [value], including default fields and documentation comments. */
    fun encode(value: T): String

    /** Deserializes YAML. Errors should identify the invalid field path when possible. */
    fun decode(yaml: String): T
}

/** Performs semantic validation after successful YAML deserialization. */
fun interface ConfigValueValidator<T> {
    /** Returns validation problems; an empty list means that the value is valid. */
    fun validate(value: T): List<ConfigProblem>
}

/** One typed configuration condition. */
fun interface ConfigCondition<T> {
    /** Returns `true` when the tested value is valid. */
    fun test(value: T): Boolean
}

/**
 * Builds readable validation rules without manually creating problem lists.
 *
 * ```kotlin
 * val validator = ConfigValidatorBuilder<Settings>()
 *     .require("database.port", "must be between 1 and 65535") { it.database.port in 1..65535 }
 *     .build()
 * ```
 */
class ConfigValidatorBuilder<T> {
    private val rules = mutableListOf<Rule<T>>()

    /** Adds a rule for the YAML [path]. */
    fun require(path: String, message: String, condition: ConfigCondition<T>) = apply {
        require(path.isNotBlank()) { "Configuration path cannot be blank" }
        rules += Rule(path, message, condition)
    }

    /** Creates an immutable validator from the configured rules. */
    fun build(): ConfigValueValidator<T> {
        val snapshot = rules.toList()
        return ConfigValueValidator { value ->
            snapshot.mapNotNull { rule ->
                if (rule.condition.test(value)) null else ConfigProblem(rule.path, rule.message)
            }
        }
    }

    private data class Rule<T>(val path: String, val message: String, val condition: ConfigCondition<T>)
}

/** One configuration problem associated with a full YAML [path]. */
data class ConfigProblem(val path: String, val message: String)

/** Result of loading and synchronizing one code-first file. */
data class ConfigLoadResult<T>(
    val value: T,
    val addedPaths: List<String>,
    val backup: File?,
    val removedPaths: List<String> = emptyList(),
    val addedComments: List<String> = emptyList(),
)

/** Semantic validation failure for a typed configuration. */
class ConfigValidationException(val problems: List<ConfigProblem>) : IllegalArgumentException(
    problems.joinToString(prefix = "Invalid configuration: ", separator = "; ") { "${it.path}: ${it.message}" }
)

/**
 * High-level lifecycle of one typed configuration.
 * `unload()` only clears the in-memory value and never deletes the YAML file.
 */
interface ManagedConfig<T> : AutoCloseable {
    /** Whether a configuration value is currently loaded in memory. */
    val isLoaded: Boolean

    /** Kotlin property alias for [get]. */
    val value: T get() = get()

    /** Returns the loaded value or fails when [load] has not completed. */
    fun get(): T

    /** Loads the file, synchronizes defaults, and stores the value in memory. */
    fun load(): ConfigLoadResult<T>

    /** Reloads the file while preserving the previous valid value on failure. */
    fun reload(): ConfigLoadResult<T>

    /** Loads the file and returns only its typed value. */
    fun loadValue(): T = load().value

    /** Reloads the file and returns only its typed value. */
    fun reloadValue(): T = reload().value

    /** Saves the current in-memory value. */
    fun save()

    /** Validates and saves [value], then makes it the current in-memory value. */
    fun save(value: T)

    /** Atomically updates, validates, and persists the current value. */
    fun update(updater: UnaryOperator<T>): T

    /** Validates the current value without writing the file. */
    fun validate(): List<ConfigProblem>

    /** Resets the file and current value to code-defined defaults. */
    fun resetToDefaults(): T

    /** Clears the in-memory value while preserving the file on disk. */
    fun unload()

    /** Alias for [unload] suitable for `use` and try-with-resources. */
    override fun close() = unload()
}
