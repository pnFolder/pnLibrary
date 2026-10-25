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
