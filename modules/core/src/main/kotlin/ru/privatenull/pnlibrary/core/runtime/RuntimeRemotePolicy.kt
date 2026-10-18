package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.plugin.DenyAction
import ru.privatenull.pnlibrary.api.plugin.ModuleId
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.plugin.RemotePolicy
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.common.jvm.JavaRuntime
import ru.privatenull.pnlibrary.core.remote.RemotePolicyMonitor
import ru.privatenull.pnlibrary.core.remote.RemotePolicyNoticeRenderer
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import java.util.concurrent.atomic.AtomicBoolean

/** Connects the existing policy engine to the library owner, without creating a synthetic module. */
internal object RuntimeRemotePolicy {
    fun validated(policy: RemotePolicy): RemotePolicy {
        require(policy.onDeny == DenyAction.DISABLE_PLUGIN) {
            "pnLibrary policy must use DISABLE_PLUGIN: the library is not a consumer module"
        }
        return RemotePolicy.builder().source(policy.source).checkEvery(policy.checkEvery)
            .onDeny(policy.onDeny).values(policy.values).build()
    }

    fun start(
        library: PnLibrary,
        platform: PlatformAdapter,
        policy: RemotePolicy,
        isClosed: () -> Boolean,
        closeRuntime: () -> Unit,
    ) {
        val started = AtomicBoolean(false)
        platform.whenServerReady(Runnable {
            if (isClosed() || !started.compareAndSet(false, true)) return@Runnable
            val owner = library.owner
            try {
                val details = platform.ownerDetails(owner)
                val metadata = PluginMetadata(
                    ModuleId.of("pnlibrary"), details["name"] ?: "pnLibrary", library.version,
                    details["authors"] ?: "pnFolder", platform.type, platform.implementationName,
                    System.getProperty("java.version", "unknown"), JavaRuntime.feature,
                )
                RemotePolicyMonitor(platform, platform, RemotePolicyNoticeRenderer(platform)).start(
                    owner = owner,
                    metadata = metadata,
                    policy = policy,
                    tasks = library.runtimeTaskScope(),
                    isClosed = isClosed,
                    logger = library.logging.logger(owner, "remote-policy"),
                    closeModule = closeRuntime,
                    closePlugin = closeRuntime,
                )
            } catch (error: Throwable) {
                // A configured policy must not silently disappear if native setup fails.
                try {
                    library.logging.logger(owner, "remote-policy").error("Could not start pnLibrary policy", error)
                } finally {
                    try { if (!platform.disableOwner(owner)) closeRuntime() }
                    catch (disableError: Throwable) {
                        error.addSuppressed(disableError)
                        closeRuntime()
                    }
                }
            }
        })
    }
}
