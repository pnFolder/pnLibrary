package ru.privatenull.pnlibrary.remote.bukkit

interface RemoteCheckListener {
    fun allowed(context: RemoteCheckContext) {}
    fun denied(context: RemoteCheckContext, reason: String) {}
    fun failed(error: Throwable) {}
}
