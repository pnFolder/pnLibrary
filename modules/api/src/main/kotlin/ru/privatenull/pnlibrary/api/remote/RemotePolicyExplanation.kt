package ru.privatenull.pnlibrary.api.remote

import java.util.Collections
import java.util.function.Consumer

/**
 * Structured, arbitrarily nested explanation returned by a remote policy.
 *
 * @property text text displayed for this explanation node
 * @property children immutable child explanations providing additional detail
 */
class RemotePolicyExplanation private constructor(builder: Builder) {
    val text: String = builder.text
    val children: List<RemotePolicyExplanation> =
        Collections.unmodifiableList(ArrayList(builder.children))

    /** Fluent builder for a structured [RemotePolicyExplanation]. */
    class Builder internal constructor(internal val text: String) {
        internal val children = mutableListOf<RemotePolicyExplanation>()

        /** Adds a leaf child containing [text]. */
        fun child(text: String) = apply { children += builder(text).build() }

        /** Adds a nested branch configured by [configure]. */
        fun branch(text: String, configure: Consumer<Builder>) = apply {
            children += builder(text).also(configure::accept).build()
        }

        /** Adds an existing explanation as a child. */
        fun child(value: RemotePolicyExplanation) = apply { children += value }

        /** Creates the immutable explanation tree. */
        fun build(): RemotePolicyExplanation = RemotePolicyExplanation(this)
    }

    /** Creates validated explanation builders. */
    companion object {
        /** Starts an explanation whose root contains non-blank [text]. */
        @JvmStatic
        fun builder(text: String): Builder {
            val normalized = text.trim()
            require(normalized.isNotEmpty()) { "remote policy explanation must not be blank" }
            return Builder(normalized)
        }
    }
}
