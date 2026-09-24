package ru.privatenull.pnlibrary.remote.bukkit

import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext

interface RemoteCheckListener {
    fun allowed(context: RemotePolicyContext) {}
    fun denied(context: RemotePolicyContext, reason: String) {}
    fun failed(error: Throwable) {}
}
