package org.pnfolder.pnlibrary.config.yaml

import org.pnfolder.pnlibrary.api.config.ConfigNodeRewriter
import org.pnfolder.pnlibrary.api.config.ConfigSerializeWith
import org.pnfolder.pnlibrary.api.config.ConfigSerializer
import org.pnfolder.pnlibrary.api.config.ConfigTextForm
import org.pnfolder.pnlibrary.api.config.ConfigTextParser
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

    /** Returns the shared instance of a [ConfigTextForm] parser. */
    @Suppress("UNCHECKED_CAST")
    fun textParser(type: Class<out ConfigTextParser<*>>):
        ConfigTextParser<Any> = synchronized(textParsers) {
        textParsers.getOrPut(type) {
            runCatching { type.getField("INSTANCE").get(null) }.getOrNull()
                ?: type.getDeclaredConstructor().also { it.isAccessible = true }.newInstance()
        } as ConfigTextParser<Any>
    }

    /** Returns the shared instance of a [org.pnfolder.pnlibrary.api.config.ConfigNodeForm] rewriter. */
    fun nodeRewriter(type: Class<out ConfigNodeRewriter>): ConfigNodeRewriter = synchronized(textParsers) {
        textParsers.getOrPut(type) {
            runCatching { type.getField("INSTANCE").get(null) }.getOrNull()
                ?: type.getDeclaredConstructor().also { it.isAccessible = true }.newInstance()
        } as ConfigNodeRewriter
    }

    private val textParsers = mutableMapOf<Class<*>, Any>()

    private fun annotationInstance(type: Class<out ConfigSerializer<*>>): ConfigSerializer<*> =
        synchronized(annotationInstances) {
            annotationInstances.getOrPut(type) {
                type.getDeclaredConstructor()
                    .also { constructor -> constructor.isAccessible = true }
                    .newInstance()
            }
        }
}
