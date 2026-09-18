package ru.privatenull.pnlibrary.api.config

import java.lang.reflect.Type

/**
 * Metadata for one value being processed by a [ConfigSerializer].
 *
 * For a field such as `database.timeout`, [declaredType] retains generic information while
 * [rawType] is the erased JVM class. [defaultValue] is useful when a serializer accepts a compact
 * representation but needs code-defined fallback metadata.
 */
data class ConfigSerializationContext(
    /** Full dot-separated YAML path of the value. */
    val path: String,
    /** Reflective declared type, including generic arguments when available. */
    val declaredType: Type,
    /** Erased JVM class corresponding to [declaredType]. */
    val rawType: Class<*>,
    /** Runtime annotations declared on the field being processed. */
    val annotations: List<Annotation>,
    /** Value supplied by the code-defined default model, or `null`. */
    val defaultValue: Any?,
) {
    /** Returns the first field annotation assignable to [type], or `null`. */
    fun <A : Annotation> annotation(type: Class<A>): A? = annotations.firstOrNull(type::isInstance)?.let(type::cast)
}

/**
 * Converts a custom type to and from YAML-compatible values.
 *
 * [serialize] should return `null`, a scalar, a list, or a string-keyed map. [deserialize] should
 * reject invalid input with a descriptive exception; pnLibrary adds [ConfigSerializationContext.path]
 * to surrounding configuration errors.
 *
 * ```kotlin
 * object DurationSerializer : ConfigSerializer<Duration> {
 *     override fun serialize(value: Duration, context: ConfigSerializationContext) = value.toString()
 *     override fun deserialize(value: Any?, context: ConfigSerializationContext) =
 *         Duration.parse(value?.toString() ?: error("duration cannot be null"))
 * }
 * ```
 */
interface ConfigSerializer<T : Any> {
    /** Converts [value] into a YAML-compatible representation. */
    fun serialize(value: T, context: ConfigSerializationContext): Any?

    /** Converts a parsed YAML [value] into the requested custom type. */
    fun deserialize(value: Any?, context: ConfigSerializationContext): T
}
