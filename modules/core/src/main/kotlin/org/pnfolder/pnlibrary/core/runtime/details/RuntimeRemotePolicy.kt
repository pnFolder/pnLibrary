package org.pnfolder.pnlibrary.core.runtime.details

import org.pnfolder.pnlibrary.api.remote.DenyAction
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginMetadata
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime
import org.pnfolder.pnlibrary.core.remote.RemotePolicyMonitor
import org.pnfolder.pnlibrary.core.remote.RemotePolicyNoticeRenderer
import org.pnfolder.pnlibrary.core.lifecycle.runtimeTaskScope
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import java.util.concurrent.atomic.AtomicBoolean

/** Connects the existing policy engine to the library owner, without creating a synthetic module. */
internal object RuntimeRemotePolicy {
    fun validated(policy: RemotePolicySettings): RemotePolicySettings {
        require(policy.onDeny == DenyAction.DISABLE_PLUGIN) {
            "pnLibrary policy must use DISABLE_PLUGIN: the library is not a consumer module"
        }
        return RemotePolicySettings.builder().source(policy.source).checkEvery(policy.checkEvery)
            .onDeny(policy.onDeny).onError(policy.onError).values(policy.values)
            .apply { policy.supportUrl?.let { support(it) } }
            .build()
    }

    fun start(
        library: PnLibrary,
        platform: PlatformAdapter,
        policy: RemotePolicySettings,
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
                    ModuleId.of(PnLibraryIdentity.PRODUCT_ID), details["name"] ?: PnLibraryIdentity.NAME, library.version,
                    details["authors"] ?: PnLibraryIdentity.ORGANIZATION, platform.type, platform.implementationName,
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
