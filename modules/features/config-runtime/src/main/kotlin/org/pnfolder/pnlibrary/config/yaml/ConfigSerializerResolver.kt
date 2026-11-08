package org.pnfolder.pnlibrary.config.yaml

import org.pnfolder.pnlibrary.api.config.ConfigNodeRewriter
import org.pnfolder.pnlibrary.api.config.ConfigSerializeWith
import org.pnfolder.pnlibrary.api.config.ConfigSerializer
import org.pnfolder.pnlibrary.api.config.ConfigTextParser
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
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

    /** The serializer for [type]: an annotation on the class, then an exact registration, then a supertype one. */
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

    /** The serializer named by `@ConfigSerializeWith` on [field], or `null`. */
    @Suppress("UNCHECKED_CAST")
    fun forField(field: Field): ConfigSerializer<Any>? =
        field.getAnnotation(ConfigSerializeWith::class.java)
            ?.value
            ?.java
            ?.let(::annotationInstance) as? ConfigSerializer<Any>

    /** The shared instance of the `@ConfigTextForm` parser [type]. */
    @Suppress("UNCHECKED_CAST")
    fun textParser(type: Class<out ConfigTextParser<*>>): ConfigTextParser<Any> = helper(type) as ConfigTextParser<Any>

    /** The shared instance of the `@ConfigNodeForm` rewriter [type]. */
    fun nodeRewriter(type: Class<out ConfigNodeRewriter>): ConfigNodeRewriter = helper(type) as ConfigNodeRewriter

    private val textParsers = mutableMapOf<Class<*>, Any>()

    // A Kotlin `object` is used as it is; any other class needs a no-argument constructor.
    private fun helper(type: Class<*>): Any = synchronized(textParsers) {
        textParsers.getOrPut(type) {
            runCatchingNonFatal { type.getField("INSTANCE").get(null) }.getOrNull()
                ?: type.getDeclaredConstructor().also { it.isAccessible = true }.newInstance()
        }
    }

    private fun annotationInstance(type: Class<out ConfigSerializer<*>>): ConfigSerializer<*> =
        synchronized(annotationInstances) {
            annotationInstances.getOrPut(type) {
                type.getDeclaredConstructor()
                    .also { constructor -> constructor.isAccessible = true }
                    .newInstance()
            }
        }
}
