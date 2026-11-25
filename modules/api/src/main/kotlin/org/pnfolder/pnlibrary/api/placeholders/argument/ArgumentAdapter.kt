package org.pnfolder.pnlibrary.api.placeholders.argument

import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo

/**
 * How text from an address becomes a value of [T].
 *
 * @property label short word for messages, such as `int` or `player`
 * @property parse the conversion; `null` when the text does not fit
 * @property choices allowed words when the type has a fixed list
 * @property me what `me` means for the viewer, or `null` when it means nothing
 */
class ArgumentAdapter<T : Any>(
    override val label: String,
    val choices: List<String> = emptyList(),
    val me: ((ViewerInfo) -> T?)? = null,
    val parse: (String) -> T?,
) : ArgumentConverter<T> {
    override fun read(input: ArgumentInput): T? = parse(input.text)

    override fun suggest(input: ArgumentInput): List<String> = choices.filter { it.startsWith(input.text, ignoreCase = true) }

    override fun meOf(viewer: ViewerInfo): T? = me?.let { provider -> provider(viewer) }
}
