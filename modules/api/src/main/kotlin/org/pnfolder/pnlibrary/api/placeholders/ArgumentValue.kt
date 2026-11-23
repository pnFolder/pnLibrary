package org.pnfolder.pnlibrary.api.placeholders

/**
 * One part of the address that the method reads: the text as written, and what it became.
 *
 * In an address there is only text. The library turns it into [T] with the adapter of that type and hands over
 * this wrapper, so the method still has the text when the conversion failed.
 *
 * @property text what was written in the address
 * @property value the converted value, or `null` when the text does not fit
 * @property error why it does not fit, or `null`
 */
class ArgumentValue<T : Any>(
    val text: String,
    val value: T?,
    val error: String?,
    private val me: ((ViewerInfo) -> T?)? = null,
) {
    /** Whether nothing was written. */
    val isEmpty: Boolean get() = text.isEmpty()

    /** Whether the text is empty or `me`: the one who looks. */
    val isMe: Boolean get() = text.isEmpty() || text.equals("me", ignoreCase = true)

    /** The value, or [default] when there is none. */
    fun orElse(default: T): T = value ?: default

    /** The value, or ends the placeholder quietly when there is none. */
    fun required(): T = value ?: throw NoValueException(error ?: "No value for '$text'")

    /** For `me` or an empty text the viewer himself, otherwise the value. */
    fun orMe(viewer: ViewerInfo): T? = if (isMe) me?.let { provider -> provider(viewer) } ?: value else value

    override fun toString(): String = "ArgumentValue(text=$text, value=$value, error=$error)"
}
