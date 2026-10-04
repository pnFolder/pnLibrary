package ru.privatenull.pnlibrary.api.remote

/** Cross-platform contract implemented by a downloaded Java or Kotlin policy. */
fun interface RemotePolicy {
    /** Evaluates [context] and returns the resulting allow/deny decision. */
    @Throws(Exception::class)
    fun check(context: RemotePolicyContext): RemotePolicyResult
}

/**
 * Decision returned by a [RemotePolicy].
 *
 * @property allowed whether startup or the guarded operation may continue
 * @property explanation structured explanation suitable for user-facing rendering
 */
class RemotePolicyResult private constructor(
    val allowed: Boolean,
    val explanation: RemotePolicyExplanation,
) {
    /** Short compatibility view; structured consumers should use [explanation]. */
    val message: String get() = explanation.text

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
    }
}
