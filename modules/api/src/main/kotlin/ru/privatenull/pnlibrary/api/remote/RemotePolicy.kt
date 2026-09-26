package ru.privatenull.pnlibrary.api.remote

/** Cross-platform contract implemented by a downloaded Java or Kotlin policy. */
fun interface RemotePolicy {
    @Throws(Exception::class)
    fun check(context: RemotePolicyContext): RemotePolicyResult
}

class RemotePolicyResult private constructor(
    val allowed: Boolean,
    val explanation: RemotePolicyExplanation,
) {
    /** Short compatibility view; structured consumers should use [explanation]. */
    val message: String get() = explanation.text

    companion object {
        @JvmStatic fun allow(): RemotePolicyResult =
            RemotePolicyResult(true, RemotePolicyExplanation.builder("Проверка пройдена").build())
        @JvmStatic fun deny(message: String): RemotePolicyResult =
            deny(RemotePolicyExplanation.builder(message.trim().ifEmpty { "Запуск запрещён" }).build())
        @JvmStatic fun deny(explanation: RemotePolicyExplanation): RemotePolicyResult =
            RemotePolicyResult(false, explanation)
    }
}
