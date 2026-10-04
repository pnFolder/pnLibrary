package ru.privatenull.pnlibrary.core.config.yaml

import ru.privatenull.pnlibrary.api.config.ConfigSerializeWith
import ru.privatenull.pnlibrary.api.config.ConfigSerializer
import java.lang.reflect.Field

/**
 * Resolves custom serializers declared by configuration types and fields.
 *
 * Annotation serializers take precedence over serializers registered at runtime.
 * A serializer instance declared by an annotation is created once and then reused.
 */
internal class ConfigSerializerResolver(
    private val registered: Map<Class<*>, ConfigSerializer<*>>,
) {
    private val annotationInstances = mutableMapOf<Class<out ConfigSerializer<*>>, ConfigSerializer<*>>()

    /** Returns the serializer responsible for [type], if one is configured. */
    @Suppress("UNCHECKED_CAST")
    fun forType(type: Class<*>): ConfigSerializer<Any>? {
        val annotated = type.getAnnotation(ConfigSerializeWith::class.java)
            ?.value
            ?.java
            ?.let(::annotationInstance)
        val exact = registered[type]
        val assignable = registered.entries
            .firstOrNull { (supportedType, _) -> supportedType.isAssignableFrom(type) }
            ?.value
        return (annotated ?: exact ?: assignable) as? ConfigSerializer<Any>
    }

    /** Returns the serializer explicitly declared on [field], if present. */
    @Suppress("UNCHECKED_CAST")
    fun forField(field: Field): ConfigSerializer<Any>? =
        field.getAnnotation(ConfigSerializeWith::class.java)
            ?.value
            ?.java
            ?.let(::annotationInstance) as? ConfigSerializer<Any>

    private fun annotationInstance(type: Class<out ConfigSerializer<*>>): ConfigSerializer<*> =
        synchronized(annotationInstances) {
            annotationInstances.getOrPut(type) {
                type.getDeclaredConstructor()
                    .also { constructor -> constructor.isAccessible = true }
                    .newInstance()
            }
        }
}
