package org.pnfolder.pnlibrary.api.placeholders.annotation

/** Keeps a property or method out of the placeholders even when its class is scanned. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Hide
