package org.pnfolder.pnlibrary.api.config

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
    fun require(path: String, message: String, condition: ConfigCondition<T>): ConfigValidatorBuilder<T> = apply {
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

    private data class Rule<T>(
        val path: String,
        val message: String,
        val condition: ConfigCondition<T>
    )
}
