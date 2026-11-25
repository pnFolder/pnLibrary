package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** Fills the `{address}` placeholders of a text, then hands the text to [outside] for the `%tokens%` of other systems. */
internal class TextRendering(
    private val resolve: (String, ViewerInfo, Map<String, Any?>, String?) -> CompletionStage<Any?>,
    private val outside: (String, UUID?) -> String,
) {
    fun render(text: String, viewer: ViewerInfo, values: Map<String, Any?>, consumer: String?): CompletionStage<String> {
        val pieces = mutableListOf<CompletableFuture<String>>()
        var from = 0
        while (true) {
            val open = text.indexOf('{', from)
            val close = if (open < 0) -1 else matchingClose(text, open)
            if (open < 0 || close < 0) break
            if (open > from) pieces += CompletableFuture.completedFuture(text.substring(from, open))
            val original = text.substring(open, close + 1)
            pieces += resolve(original, viewer, values, consumer).toCompletableFuture().handle { value, _ -> value?.toString() ?: original }
            from = close + 1
        }
        if (from < text.length) pieces += CompletableFuture.completedFuture(text.substring(from))
        return CompletableFuture.allOf(*pieces.toTypedArray())
            .thenApply { pieces.joinToString("") { it.join() } }
            .thenApply { filled -> outside(filled, viewer.playerId) }
    }

    /** The `}` that closes the `{` at [open], with `{}` pairs inside skipped; `-1` when it is not closed. */
    private fun matchingClose(text: String, open: Int): Int {
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return index
            }
        }
        return -1
    }
}
