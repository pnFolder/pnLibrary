package org.pnfolder.pnlibrary.api.config

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
    /** Reads a nested value the way the configuration does, or `null` when used outside a configuration load. */
    val decoder: ConfigValueDecoder? = null,
    /** Writes a nested value the way the configuration does, or `null` when used outside a configuration save. */
    val encoder: ConfigValueEncoder? = null,
) {
    /** Returns the first field annotation assignable to [type], or `null`. */
    fun <A : Annotation> annotation(type: Class<A>): A? = annotations.firstOrNull(type::isInstance)?.let(type::cast)

    /**
     * Reads [value], found at [path], as [type] with every rule of the configuration: polymorphic `type`,
     * text forms, custom serializers and error locations.
     *
     * @throws IllegalStateException when this context has no [decoder]
     */
    fun <T : Any> decode(value: Any?, type: Class<T>, path: String): T =
        type.cast(checkNotNull(decoder) { "Nested values cannot be read here" }.decode(value, type, path))

    /**
     * Writes [value] as [type] with every rule of the configuration, including the `type` of polymorphic values.
     *
     * @throws IllegalStateException when this context has no [encoder]
     */
    fun encode(value: Any?, type: Type, path: String): Any? =
        checkNotNull(encoder) { "Nested values cannot be written here" }.encode(value, type, path)
}

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
 * Reads a polymorphic value from one line of text instead of a YAML object.
 *
 * Declare it with [ConfigTextForm] on the polymorphic base type. The line `"[message] Hello"` can then
 * stand where the full object `{ type: message, messages: [Hello] }` is accepted. Objects keep working.
 *
 * Implementations should reject invalid text with a descriptive exception; pnLibrary adds the path.
 */
interface ConfigTextParser<T : Any> {
    /** Converts [text] into a value of the polymorphic base type. */
    fun parse(text: String, context: ConfigSerializationContext): T
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
 * Rewrites a YAML object into the object the polymorphic codec expects, before its `type` is read.
 *
 * Declare it with [ConfigNodeForm] on the polymorphic base type. It lets an object without a `type`
 * (a wrapper such as `delay: 2s` with a `do:` list) stand where an action is accepted.
 *
 * Implementations return [node] unchanged when they do not recognise it.
 */
interface ConfigNodeRewriter {
    /** Returns the object to decode in place of [node]; [path] is the location used in messages. */
    fun rewrite(node: Map<String, Any?>, path: String): Map<String, Any?>
}

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
