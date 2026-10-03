package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.plugin.ModuleId
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object ModuleServiceKeyFactory {
    fun create(registrationId: Long, nativePlugin: PluginId, module: ModuleId): PluginId {
        // The same plugin and module IDs may appear again after a reload. The registration ID
        // keeps service ownership tied to one live plugin instance rather than its public name.
        val identity = "${nativePlugin.value}\u0000${module.value}\u0000$registrationId"
        val identityHash = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

        return PluginId.of(
            "m-${nativePlugin.value.take(18)}-${module.value.take(18)}-${identityHash.take(16)}",
        )
    }
}
