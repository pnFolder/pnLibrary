package org.pnfolder.pnlibrary.api.placeholders

/** What a [Reply] holds. */
enum class ReplyKind {
    /** An answer: the address ends here with this value. */
    VALUE,

    /** An object that continues the address: the next words are its `@Placeholder` members. */
    NEXT,

    /** An answer given from a branch: the address ends here and nothing below is called. */
    STOP,

    /** No value: the address stays as it was written. */
    NONE,
}

/**
 * The result of a placeholder method that has to say what it is: an answer, a way to go on, or nothing.
 * Every `@Placeholder` method or property returns a [Reply] (or a `CompletableFuture` of one); a plain return type is refused when the class is registered.
 *
 * The type tells what the method is: `Reply<Int>` is a value (the address ends), `Reply<WarInfo>` is a branch
 * (the address goes on in the `@Placeholder` members of `WarInfo`).
 *
 * ```
 * @Placeholder fun level(@Viewer viewer: ViewerInfo): Reply<Int> =
 *     Clans.of(viewer.playerId)?.let { Reply.value(it.level) } ?: Reply.none()
 *
 * @Placeholder fun war(@Viewer viewer: ViewerInfo): Reply<WarInfo> =
 *     Clans.of(viewer.playerId)?.let { Reply.next(it.war) } ?: Reply.stop("no clan")
 * ```
 *
 * @property kind what the reply is
 * @property content the value, the object to go on with, or the answer; `null` for [ReplyKind.NONE]
 * @property format the format applied to the value when the address names none, or `null`
 */
class Reply<out T> private constructor(val kind: ReplyKind, val content: Any?, val format: String?) {
    override fun toString(): String = "Reply($kind, $content)"

    companion object {
        /** An answer. [format] is applied when the address names no format of its own: `Reply.value(1250, format = "coins")`. */
        @JvmStatic
        @JvmOverloads
        fun <T> value(value: T, format: String? = null): Reply<T> = Reply(ReplyKind.VALUE, value, format)

        /** An answer when [value] is there, otherwise [none]. For values that may be missing: `Reply.of(Clans.of(id)?.level)`. */
        @JvmStatic
        fun <T : Any> of(value: T?): Reply<T> = if (value == null) Reply(ReplyKind.NONE, null, null) else Reply(ReplyKind.VALUE, value, null)

        /** An object to go on with: the address continues in its `@Placeholder` members. `null` means no value. */
        @JvmStatic
        fun <T : Any> next(next: T?): Reply<T> = if (next == null) Reply(ReplyKind.NONE, null, null) else Reply(ReplyKind.NEXT, next, null)

        /** An answer given from a branch: the address ends here with [answer] and nothing below is called. */
        @JvmStatic
        @JvmOverloads
        fun stop(answer: Any?, format: String? = null): Reply<Nothing> =
            if (answer == null) Reply(ReplyKind.NONE, null, null) else Reply(ReplyKind.STOP, answer, format)

        /** No value: the address stays as it was written. */
        @JvmStatic
        fun none(): Reply<Nothing> = Reply(ReplyKind.NONE, null, null)
    }
}
