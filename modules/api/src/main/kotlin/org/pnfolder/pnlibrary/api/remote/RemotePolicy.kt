package org.pnfolder.pnlibrary.api.remote

/** Cross-platform contract implemented by a downloaded Java policy. */
fun interface RemotePolicy {
    /** Evaluates [context] and returns the resulting allow/deny decision. */
    @Throws(Exception::class)
    fun check(context: RemotePolicyContext): RemotePolicyResult
}

/**
 * Decision returned by a [RemotePolicy].
 *
 * Besides the allow/deny decision a policy can shape the console card the administrator sees: a [status] shown
 * instead of the default one, a [headline] under the mascot, labelled [details], [steps] to follow and [links].
 * Everything but the decision is optional; [allow] and [deny] keep working, [allowing] and [denying] open the builder.
 *
 * @property allowed whether startup or the guarded operation may continue
 * @property explanation structured explanation suitable for user-facing rendering
 * @property status short state shown next to the installed version, or `null` for the default
 * @property headline line under the mascot, or `null` for the default
 * @property details labelled facts such as the minimum supported version, in insertion order
 * @property steps what the administrator should do, in order
 * @property links labelled addresses such as a download page, in insertion order
 */
class RemotePolicyResult private constructor(
    val allowed: Boolean,
    val explanation: RemotePolicyExplanation,
    val status: String? = null,
    val headline: String? = null,
    val details: Map<String, String> = emptyMap(),
    val steps: List<String> = emptyList(),
    val links: Map<String, String> = emptyMap(),
) {
    /** Short compatibility view; structured consumers should use [explanation]. */
    val message: String get() = explanation.text

    /** Fluent builder for a decision with a custom card. */
    class Builder internal constructor(private val allowed: Boolean, text: String) {
        private val reasons = RemotePolicyExplanation.builder(text)
        private var status: String? = null
        private var headline: String? = null
        private val details = linkedMapOf<String, String>()
        private val steps = mutableListOf<String>()
        private val links = linkedMapOf<String, String>()

        /** Sets the state shown next to the installed version, for example "устарела". */
        fun status(value: String): Builder = apply { status = value.trim().ifEmpty { null } }

        /** Sets the line under the mascot. */
        fun headline(value: String): Builder = apply { headline = value.trim().ifEmpty { null } }

        /** Adds a line to the reasons tree. */
        fun reason(text: String): Builder = apply { reasons.child(text) }

        /** Adds a nested group of reasons. */
        fun reason(text: String, configure: java.util.function.Consumer<RemotePolicyExplanation.Builder>): Builder =
            apply { reasons.branch(text, configure) }

        /** Adds a labelled fact to the card. */
        fun detail(label: String, value: Any): Builder = apply {
            require(label.isNotBlank()) { "Detail label must not be blank" }
            details[label.trim()] = value.toString()
        }

        /** Adds a step under "what to do". */
        fun step(text: String): Builder = apply {
            require(text.isNotBlank()) { "Step must not be blank" }
            steps += text.trim()
        }

        /** Adds a labelled address, for example where to download the update. */
        fun link(label: String, url: String): Builder = apply {
            require(label.isNotBlank() && url.isNotBlank()) { "Link label and address must not be blank" }
            links[label.trim()] = url.trim()
        }

        /** Creates the immutable decision. */
        fun build(): RemotePolicyResult = RemotePolicyResult(
            allowed, reasons.build(), status, headline,
            java.util.Collections.unmodifiableMap(LinkedHashMap(details)),
            java.util.Collections.unmodifiableList(ArrayList(steps)),
            java.util.Collections.unmodifiableMap(LinkedHashMap(links)),
        )
    }

    /** Factory methods for common policy decisions. */
    companion object {
        /** Creates an allowed result with a standard success explanation. */
        @JvmStatic
        fun allow(): RemotePolicyResult =
            RemotePolicyResult(true, RemotePolicyExplanation.builder("Проверка пройдена").build())

        /** Creates a denied result from a plain-text [message]. */
        @JvmStatic
        fun deny(message: String): RemotePolicyResult =
            deny(RemotePolicyExplanation.builder(message.trim().ifEmpty { "Запуск запрещён" }).build())

        /** Creates a denied result using a structured [explanation]. */
        @JvmStatic
        fun deny(explanation: RemotePolicyExplanation): RemotePolicyResult =
            RemotePolicyResult(false, explanation)

        /** Starts an allowed decision whose card can be customised. */
        @JvmStatic
        fun allowing(message: String): Builder = Builder(true, message.trim().ifEmpty { "Проверка пройдена" })

        /** Starts a denied decision whose card can be customised. */
        @JvmStatic
        fun denying(message: String): Builder = Builder(false, message.trim().ifEmpty { "Запуск запрещён" })
    }
}
