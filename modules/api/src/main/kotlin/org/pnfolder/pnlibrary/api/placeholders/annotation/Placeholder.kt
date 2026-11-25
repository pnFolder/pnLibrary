package org.pnfolder.pnlibrary.api.placeholders.annotation

import org.pnfolder.pnlibrary.api.placeholders.reply.Reply
import org.pnfolder.pnlibrary.api.registry.Visibility

/**
 * Makes a method or a property a placeholder, named after it in lower case. It returns a [Reply]. A method whose reply holds a plain value
 * is a leaf; a method that returns an object becomes a branch whose own `@Placeholder` members continue the address.
 *
 * @property value description shown in `/pn placeholders`
 * @property onInvalid what to show when there is no value
 * @property default the text for [Invalid.DEFAULT]
 *
 * On a branch, an [onInvalid] other than [Invalid.KEEP] covers every text placeholder below it that sets none of its own.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Placeholder(
    val value: String = "",
    val onInvalid: Invalid = Invalid.KEEP,
    val default: String = "",
    val visibility: Visibility = Visibility.ALL,
)
