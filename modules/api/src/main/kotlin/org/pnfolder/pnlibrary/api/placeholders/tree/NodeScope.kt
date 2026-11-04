package org.pnfolder.pnlibrary.api.placeholders.tree

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * One branch of a placeholder tree. It can give its own [value], be formatted, restricted or changed from outside,
 * and carry further branches.
 *
 * @param T the type of value of this branch
 * @param A the type of the last argument on the path
 */
class NodeScope<T : Any, A> internal constructor(
    private val node: Node,
) : BranchScope<T, A>(node.children, node.valueType) {
    /** The value of this branch. `null` means there is no value now. */
    @Suppress("UNCHECKED_CAST")
    fun value(block: PlaceholderCall<A>.() -> T?) {
        node.value = { call -> CompletableFuture.completedFuture((call as PlaceholderCall<A>).block()) }
    }

    /** The value of this branch when it takes time to get; the server does not wait for it. */
    @Suppress("UNCHECKED_CAST")
    fun valueAsync(block: PlaceholderCall<A>.() -> CompletionStage<T?>) {
        node.value = { call -> (call as PlaceholderCall<A>).block() as CompletionStage<Any?> }
    }

    /** The format applied when the text does not name one, such as `format("money")`. */
    fun format(name: String) {
        node.format = FormatChoice.Named(name)
    }

    /** No default format for this branch, even if the group has one. */
    fun format(none: none) {
        node.format = FormatChoice.None
    }

    /** Who may read this branch; the branches below inherit it. */
    fun access(block: AccessScope.() -> Unit) {
        node.access = AccessScope().apply(block).rule
    }

    /** Keeps this branch and everything below it out of PlaceholderAPI. */
    fun inside() {
        node.inside = true
    }

    /**
     * Lets [rule] change the value from outside with [block], which gets the new text.
     * Without this the placeholder is read-only.
     */
    @Suppress("UNCHECKED_CAST")
    fun update(rule: AccessRule, block: PlaceholderCall<A>.(String) -> Unit) {
        node.updater = Updater(rule) { call, text -> (call as PlaceholderCall<A>).block(text); null }
    }
}
