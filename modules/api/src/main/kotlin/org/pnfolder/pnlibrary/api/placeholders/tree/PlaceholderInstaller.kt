package org.pnfolder.pnlibrary.api.placeholders.tree

import org.pnfolder.pnlibrary.api.placeholders.AsyncPlaceholderResolver
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderBuilder
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderPublication
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderRequest
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderUpdater
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Consumer

/** Turns a placeholder tree into flat registered placeholders whose names are the paths of the tree. */
internal class PlaceholderInstaller(
    private val service: PlaceholderService,
    private val moduleId: String,
) {
    /** Registers the placeholder group with the platform. */
    fun install(group: GroupData) {
        val format = group.format.resolve(null)
        group.roots.forEach { walk(group, it, emptyList(), format, group.access, false) }
    }

    private fun walk(group: GroupData, node: Node, parent: List<Segment>, format: String?, access: AccessRule, inside: Boolean) {
        val path = parent + node.segment
        val nodeFormat = node.format.resolve(format)
        val nodeAccess = node.access ?: access
        val hidden = inside || node.inside
        if (node.value != null) register(group, node, path, nodeFormat, nodeAccess, hidden)
        node.children.forEach { walk(group, it, path, nodeFormat, nodeAccess, hidden) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun register(group: GroupData, node: Node, path: List<Segment>, format: String?, access: AccessRule, hidden: Boolean) {
        val key = keyOf(path)
        service.register(key, node.valueType as Class<Any>, Consumer<PlaceholderBuilder<Any>> { builder ->
            builder.resolveAsync(AsyncPlaceholderResolver { request -> evaluate(node, path, request) })
            builder.access(access.toAccess())
            format?.let(builder::defaultFormat)
            node.updater?.let { updater ->
                builder.updateAccess(updater.rule.toAccess())
                builder.update(PlaceholderUpdater { request, text -> change(updater, path, request, text) })
            }
            val prefix = group.papiPrefix
            if (prefix != null && !hidden) builder.publish(PlaceholderPublication("placeholderapi", prefix, "$moduleId~$key"))
        })
    }

    private fun evaluate(node: Node, path: List<Segment>, request: PlaceholderRequest): CompletionStage<Any?> {
        val call = callFor(path, request) ?: return CompletableFuture.completedFuture(null)
        return node.value!!(call)
    }

    private fun change(updater: Updater, path: List<Segment>, request: PlaceholderRequest, text: String): Any? {
        val call = callFor(path, request) ?: throw IllegalArgumentException("The path does not fit this placeholder")
        return updater.run(call, text)
    }

    /** Reads every argument of the path from the request; `null` when one of them does not fit its type. */
    private fun callFor(path: List<Segment>, request: PlaceholderRequest): PlaceholderCall<Any?>? {
        val arguments = LinkedHashMap<String, Any>()
        var last: Any? = null
        for (segment in path) {
            if (segment !is Segment.Arg) continue
            val raw = request.parameters[segment.name] ?: return null
            val parsed = segment.type.parse(raw) ?: return null
            arguments[segment.name] = parsed
            last = parsed
        }
        return PlaceholderCall(request, arguments, last)
    }

    private fun keyOf(path: List<Segment>): String = path.joinToString(".") { segment ->
        when (segment) {
            is Segment.Literal -> segment.name
            is Segment.Arg -> "{${segment.name}}"
        }
    }
}
