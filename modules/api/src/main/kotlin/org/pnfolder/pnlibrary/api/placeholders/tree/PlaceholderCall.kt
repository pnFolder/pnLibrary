package org.pnfolder.pnlibrary.api.placeholders.tree

import org.pnfolder.pnlibrary.api.placeholders.PlaceholderRequest
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.UUID

/**
 * What the body of a placeholder sees: who is asking, for whom, and the arguments written in the path.
 *
 * @param A the type of the last argument of the path, or [Unit] when the path has none
 */
class PlaceholderCall<A> internal constructor(
    /** The raw request, for anything this class does not cover. */
    val request: PlaceholderRequest,
    private val arguments: Map<String, Any>,
    private val last: Any?,
) {
    /** The player the text is rendered for, or `null` for a value that does not belong to a player. */
    val player: UUID? get() = request.playerId

    /** The plugin that asks for the value. */
    val caller: PluginId get() = request.consumer

    /** Values handed over by the caller of `render`. */
    val values: Map<String, Any?> get() = request.values

    /** The last argument of the path. */
    @Suppress("UNCHECKED_CAST")
    val arg: A get() = last as A

    /** The argument named [name], from anywhere on the path. */
    @Suppress("UNCHECKED_CAST")
    fun <X> argOf(name: String): X = (arguments[name] ?: error("The path has no argument '$name'")) as X
}
