package org.pnfolder.pnlibrary.api.config

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
 * One semantic configuration problem.
 *
 * @property path full dot-separated YAML path, such as `database.pool-size`
 * @property message human-readable explanation without the path prefix
 */
data class ConfigProblem(
    val path: String,
    val message: String,
)

/**
 * Aggregated semantic validation failure for a typed configuration.
 *
 * @property problems immutable-style list of every problem reported by the validator
 */
class ConfigValidationException(
    val problems: List<ConfigProblem>,
) : IllegalArgumentException(
    problems.joinToString(prefix = "Invalid configuration: ", separator = "; ") { "${it.path}: ${it.message}" }
)

/**
 * High-level lifecycle of one typed configuration file.
 *
 * A handle starts unloaded. [load] or [reload] parses, synchronizes, and validates the complete
 * document before replacing the in-memory value. Failed reloads leave the last valid value intact.
 * [unload] only clears memory and never deletes or rewrites the YAML file.
 */
interface ManagedConfig<T> : AutoCloseable {
    /** Whether a configuration value is currently loaded in memory. */
    val isLoaded: Boolean

    /** Kotlin property alias for [get]. */
    val value: T get() = get()

    /**
     * Returns the loaded value.
     *
     * @throws IllegalStateException before a successful [load] or after [unload]
     */
    fun get(): T

    /**
     * Loads the file, applies migrations and synchronization policies, validates it, and stores
     * the value in memory. A missing file is handled according to [ConfigOptions.missingFile].
     */
    fun load(): ConfigLoadResult<T>

    /** Reloads the file while preserving the previous valid value on failure. */
    fun reload(): ConfigLoadResult<T>

    /** Loads the file and returns only its typed value. */
    fun loadValue(): T = load().value

    /** Reloads the file and returns only its typed value. */
    fun reloadValue(): T = reload().value

    /** Saves the current in-memory value after validation. */
    fun save()

    /**
     * Validates and saves [value], then makes it current.
     *
     * The in-memory value is changed only after validation and persistence succeed.
     */
    fun save(value: T)

    /**
     * Applies [updater] to the current value, validates and persists the result, then returns it.
     *
     * Implementations serialize concurrent calls. If the updater, validation, or write fails, the
     * previously loaded value remains current.
     */
    fun update(updater: UnaryOperator<T>): T

    /** Validates the current value without writing the file. */
    fun validate(): List<ConfigProblem>

    /** Replaces the file and current value with a freshly obtained code-defined default value. */
    fun resetToDefaults(): T

    /** Clears the in-memory value while preserving the file on disk. */
    fun unload()

    /** Alias for [unload] suitable for `use` and try-with-resources. */
    override fun close() = unload()
}
