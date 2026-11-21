package org.pnfolder.pnlibrary.api.remote

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

        /** Adds a nested line with plain text. */
        fun child(text: String): Builder = apply { children += builder(text).build() }

        /** Adds a nested line whose own children are configured by [configure]. */
        fun branch(text: String, configure: Consumer<Builder>): Builder = apply {
            children += builder(text).also(configure::accept).build()
        }

        /** Adds an already built explanation as a nested line. */
        fun child(value: RemotePolicyExplanation): Builder = apply { children += value }

        /** Creates the immutable explanation. */
        fun build(): RemotePolicyExplanation = RemotePolicyExplanation(this)
    }

    companion object {
        /** Starts an explanation whose root contains non-blank [text]. */
        @JvmStatic
        fun builder(text: String): Builder {
            val normalized = text.trim()
            require(normalized.isNotEmpty()) { "Remote policy explanation must not be blank" }
            return Builder(normalized)
        }
    }
}
