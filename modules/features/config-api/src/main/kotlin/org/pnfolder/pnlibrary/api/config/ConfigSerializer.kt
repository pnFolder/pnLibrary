package org.pnfolder.pnlibrary.api.config

import java.lang.reflect.Type

/** Writes one nested configuration value; supplied by the configuration runtime. */
fun interface ConfigValueEncoder {
    /** Converts [value], declared as [type] at [path], into YAML-compatible data. */
    fun encode(value: Any?, type: Type, path: String): Any?
}

/** Reads one nested configuration value; supplied by the configuration runtime. */
fun interface ConfigValueDecoder {
    /** Converts [value], found at [path], into [type]. */
    fun decode(value: Any?, type: Type, path: String): Any?
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
 *         Duration.parse(value?.toString() ?: error("Duration must not be null"))
 * }
 * ```
 */
interface ConfigSerializer<T : Any> {
    /** Converts [value] into a YAML-compatible representation. */
    fun serialize(value: T, context: ConfigSerializationContext): Any?

    /** Converts a parsed YAML [value] into the requested custom type. */
    fun deserialize(value: Any?, context: ConfigSerializationContext): T
}

/**
 * Lets a polymorphic base type be written as a single line of text, read by [value].
 *
 * The parser class must expose a no-argument constructor or Kotlin `object` instance.
 *
 * @property value parser applied when the YAML value is a string
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigTextForm(val value: kotlin.reflect.KClass<out ConfigTextParser<*>>)

/**
 * Lets a polymorphic base type accept objects without a `type`, converted by [value] first.
 *
 * The rewriter class must expose a no-argument constructor or Kotlin `object` instance.
 *
 * @property value rewriter applied to every object read for the base type
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigNodeForm(val value: kotlin.reflect.KClass<out ConfigNodeRewriter>)
