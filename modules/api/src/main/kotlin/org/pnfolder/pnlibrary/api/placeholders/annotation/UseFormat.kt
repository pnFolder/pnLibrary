package org.pnfolder.pnlibrary.api.placeholders.annotation

/**
 * Applies the format [value] when the text names none. On a method it covers that placeholder, on a class every
 * placeholder under it whose value is of [type] (any type when it is left out).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class UseFormat(val value: String, val type: kotlin.reflect.KClass<*> = Any::class)
