package ru.privatenull.pnlibrary.api.remote

/** Cross-platform contract implemented by a downloaded Java or Kotlin policy. */
fun interface RemotePolicy {
    @Throws(Exception::class)
    fun check(context: RemotePolicyContext): RemotePolicyResult
}

class RemotePolicyResult private constructor(
    val allowed: Boolean,
    val message: String,
) {
    companion object {
        @JvmStatic fun allow(): RemotePolicyResult = RemotePolicyResult(true, "")
        @JvmStatic fun deny(message: String): RemotePolicyResult =
            RemotePolicyResult(false, message.trim().ifEmpty { "remote policy denied execution" })
    }
}
