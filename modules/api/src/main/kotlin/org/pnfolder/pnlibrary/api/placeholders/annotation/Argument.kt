package org.pnfolder.pnlibrary.api.placeholders.annotation

import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue

/**
 * Marks a parameter of type [ArgumentValue]: the part of the address it reads. Every such parameter needs it; the options are all optional.
 *
 * @property range allowed numbers for an integer argument, such as `"1..100"`
 * @property choices allowed words for a text argument
 * @property name the name of the path part, for lists and error messages
 * @property converter the class that reads the text, for a type that has no default or needs another reading; see [ArgumentConverter]
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Argument(
    val range: String = "",
    val choices: Array<String> = [],
    val name: String = "",
    val converter: kotlin.reflect.KClass<out ArgumentConverter<*>> = ArgumentConverter::class,
)
