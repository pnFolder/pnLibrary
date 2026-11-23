package org.pnfolder.pnlibrary.api.placeholders

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Everything a converter knows when it turns one part of an address into a value.
 *
 * @property text what was written, such as `Wolves`
 * @property viewer who asks
 * @property route the whole asked address, every part with its kind
 * @property name the name of the parameter that receives the value
 * @property position the place of this argument among the arguments of the address, from 0
 * @property previous the arguments already read to the left of this one
 */
class ArgumentInput(
    val text: String,
    val viewer: ViewerInfo,
    val route: RouteInfo,
    val name: String,
    val position: Int,
    val previous: List<ArgumentValue<*>>,
) {
    /** Says why the text does not fit. The reason is shown by `/pn placeholders test` and kept in [ArgumentValue.error]. */
    fun fail(reason: String): Nothing = throw ArgumentRejected(reason)
}

/** Thrown by [ArgumentInput.fail]; it is the answer "this text does not fit", not an error of the plugin. */
class ArgumentRejected(val reason: String) : RuntimeException(reason, null, false, false)

/**
 * Turns the text of one argument into a value of [T]. Write one for a class that is not yours, and name it on the
 * parameter: `@Argument(converter = ClanByName::class) clan: ArgumentValue<Clan>`. The class needs a constructor
 * without parameters (or is a Kotlin `object`). A converter registered with [ArgumentAdapters.register] is the
 * default for its type.
 */
interface ArgumentConverter<T : Any> {
    /** Short words for messages and lists: `clan`, `number 1..9`. */
    val label: String

    /** The value for [ArgumentInput.text], or `null` when it does not fit. [ArgumentInput.fail] tells why. */
    fun read(input: ArgumentInput): T?

    /**
     * The same, for work that takes time (a database). The default calls [read]; override this one instead of
     * [read] only when the answer really comes later.
     */
    fun readLater(input: ArgumentInput): CompletionStage<T?> = CompletableFuture.completedFuture(read(input))

    /** The words that fit here, for commands and lists. [ArgumentInput.text] is what is already typed. */
    fun suggest(input: ArgumentInput): List<String> = emptyList()

    /** What `me` means for [viewer] (the one who looks), or `null`. */
    fun meOf(viewer: ViewerInfo): T? = null
}
