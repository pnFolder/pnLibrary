package ru.privatenull.pnlibrary.api.config

import java.lang.reflect.Type

/** Location and declared type of one value processed by a custom serializer. */
data class ConfigSerializationContext(
    val path: String,
    val declaredType: Type,
    val rawType: Class<*>,
    val annotations: List<Annotation>,
    val defaultValue: Any?,
) {
    fun <A : Annotation> annotation(type: Class<A>): A? = annotations.firstOrNull(type::isInstance)?.let(type::cast)
}

/** Converts a custom type to/from YAML-compatible scalar, list, or map values. */
interface ConfigSerializer<T : Any> {
    fun serialize(value: T, context: ConfigSerializationContext): Any?
    fun deserialize(value: Any?, context: ConfigSerializationContext): T
}
