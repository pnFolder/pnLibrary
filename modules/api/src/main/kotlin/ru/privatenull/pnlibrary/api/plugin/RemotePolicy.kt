package ru.privatenull.pnlibrary.api.plugin

import java.time.Duration

enum class DenyAction { DISABLE_PLUGIN, DISABLE_MODULE }

data class RemotePolicy(
    val source: String,
    val checkEvery: Duration = Duration.ofHours(6),
    val onDeny: DenyAction = DenyAction.DISABLE_PLUGIN,
)

class RemotePolicyBuilder {
    private var source: String? = null
    private var checkEvery: Duration = Duration.ofHours(6)
    private var onDeny: DenyAction = DenyAction.DISABLE_PLUGIN
    fun source(value: String) { require(value.startsWith("https://")); source = value }
    fun checkEvery(value: Duration) { require(!value.isZero && !value.isNegative); checkEvery = value }
    fun onDeny(value: DenyAction) { onDeny = value }
    fun build(): RemotePolicy = RemotePolicy(requireNotNull(source) { "remote policy source is required" }, checkEvery, onDeny)
}
