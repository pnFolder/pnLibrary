package ru.privatenull.pnlibrary.api.remote

import java.util.Collections
import java.util.function.Consumer

/** Structured, arbitrarily nested explanation returned by a remote policy. */
class RemotePolicyExplanation private constructor(builder: Builder) {
    val text: String = builder.text
    val children: List<RemotePolicyExplanation> =
        Collections.unmodifiableList(ArrayList(builder.children))

    class Builder internal constructor(internal val text: String) {
        internal val children = mutableListOf<RemotePolicyExplanation>()

        fun child(text: String) = apply { children += builder(text).build() }

        fun branch(text: String, configure: Consumer<Builder>) = apply {
            children += builder(text).also(configure::accept).build()
        }

        fun child(value: RemotePolicyExplanation) = apply { children += value }
        fun build(): RemotePolicyExplanation = RemotePolicyExplanation(this)
    }

    companion object {
        @JvmStatic fun builder(text: String): Builder {
            val normalized = text.trim()
            require(normalized.isNotEmpty()) { "remote policy explanation must not be blank" }
            return Builder(normalized)
        }
    }
}
