package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput
import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo
import org.pnfolder.pnlibrary.api.placeholders.route.RouteKind
import org.pnfolder.pnlibrary.api.placeholders.route.RoutePart
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** A placeholder found in the tree and the nodes that lead to it. */
internal class Match(val node: PhNode, val path: List<PhNode>)

/** Walks a placeholder address down the tree, reads its arguments and applies the formats to the answer. */
internal class PlaceholderEvaluation(private val formats: FormatRegistry, private val report: (String) -> Unit) {
    fun find(node: PhNode, words: List<String>, index: Int, path: List<PhNode>): Match? {
        if (index == words.size) return if (node.value != null) Match(node, path) else null
        val word = words[index]
        for (child in node.children) {
            val part = child.part
            if (part is PhPart.Word && part.name.equals(word, ignoreCase = true)) {
                find(child, words, index + 1, path + child)?.let { return it }
            }
        }
        for (child in node.children) {
            val part = child.part
            if (part is PhPart.Slot) find(child, words, index + 1, path + child)?.let { return it }
        }
        return null
    }

    private fun routeOf(match: Match, address: ParsedAddress): RouteInfo {
        // The field is the last word; arguments after it (`stats.kills.Alex`) belong to it.
        val lastWord = match.path.indexOfLast { it.part is PhPart.Word }
        val parts = match.path.mapIndexed { index, node ->
            val text = address.words[index]
            when (val part = node.part) {
                is PhPart.Slot -> RoutePart(RouteKind.ARGUMENT, text, part.name)
                is PhPart.Word -> when (index) {
                    0 -> RoutePart(RouteKind.GROUP, text, part.name)
                    lastWord -> RoutePart(RouteKind.FIELD, text, part.name)
                    else -> RoutePart(RouteKind.BRANCH, text, part.name)
                }
            }
        } + address.formats.map { RoutePart(RouteKind.FORMAT, it.name, it.name, it.arguments) }
        return RouteInfo(parts)
    }

    /** Reads the arguments of the path one after another, left to right; each converter sees the ones before it. */
    private fun readSlots(match: Match, address: ParsedAddress, route: RouteInfo, viewer: ViewerInfo): CompletionStage<Map<String, ParsedArgument>> {
        var chain: CompletionStage<Map<String, ParsedArgument>> = CompletableFuture.completedFuture(emptyMap())
        var position = 0
        match.path.forEachIndexed { index, node ->
            val part = node.part as? PhPart.Slot ?: return@forEachIndexed
            val text = address.words[index]
            val here = position++
            chain = chain.thenCompose { done ->
                val input = ArgumentInput(text, viewer, route, part.name, here, done.values.map { it.argument })
                part.type.convert(input).thenApply { done + (part.name to it) }
            }
        }
        return chain
    }

    fun evaluate(match: Match, address: ParsedAddress, viewer: ViewerInfo, original: String, consumer: String?): CompletionStage<Any?> {
        val route = routeOf(match, address)
        return readSlots(match, address, route, viewer).thenCompose { slots ->
            val stage = try {
                checkNotNull(match.node.value)(Call(viewer, slots, route))
            } catch (failure: Throwable) {
                report("Placeholder $original failed: ${failure.message ?: failure.javaClass.simpleName}")
                return@thenCompose CompletableFuture.completedFuture<Any?>(null)
            }
            stage.handle { value, failure ->
                if (failure != null) {
                    report("Placeholder $original failed: ${failure.cause?.message ?: failure.message ?: failure.javaClass.simpleName}")
                    null
                } else if (value is Formatted) {
                    applyFormats(value.value, match.node, address.formats, viewer, original, value.format, consumer)
                } else {
                    applyFormats(value, match.node, address.formats, viewer, original, null, consumer)
                }
            }
        }
    }

    fun applyFormats(
        value: Any?,
        node: PhNode?,
        written: List<FormatCall>,
        viewer: ViewerInfo,
        original: String,
        own: String?,
        consumer: String?,
    ): Any? {
        val explicit = written.filter { it.name != "raw" }
        val chain = when {
            explicit.isNotEmpty() -> explicit
            written.isNotEmpty() -> emptyList()
            own != null -> listOf(FormatCall(own, emptyList()))
            node?.format != null -> listOf(FormatCall(checkNotNull(node.format), emptyList()))
            else -> emptyList()
        }
        if (value == null) return chain.firstOrNull { it.name == "default" }?.arguments?.joinToString(",")
        var current: Any = value
        for (call in chain) {
            val format = formats.find(call.name, current, consumer, call.owner)
            if (format == null) {
                report("Placeholder $original: no format '${call.name}' fits ${current.javaClass.simpleName}")
                continue
            }
            current = try {
                format.apply(current, call.arguments, viewer) ?: return null
            } catch (failure: Exception) {
                report("Placeholder $original: format '${call.name}' failed: ${failure.message ?: failure.javaClass.simpleName}")
                return null
            }
        }
        return current
    }
}
