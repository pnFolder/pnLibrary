package org.pnfolder.pnlibrary.api.placeholders.argument

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

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
