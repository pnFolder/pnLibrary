package ru.privatenull.pnlibrary.remote.bukkit

class RemoteCheckResult private constructor(val allowed: Boolean, val message: String) {
    fun allowed() = allowed
    fun message() = message
    companion object {
        @JvmStatic fun allow() = RemoteCheckResult(true, "")
        @JvmStatic fun deny(message: String) = RemoteCheckResult(false, message.trim().ifEmpty { "remote policy denied execution" })
    }
}
