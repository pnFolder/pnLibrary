package ru.privatenull.pnlibrary.remote.bukkit

fun interface RemoteCheck {
    @Throws(Exception::class)
    fun check(context: RemoteCheckContext): RemoteCheckResult
}
