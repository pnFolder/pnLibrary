package org.pnfolder.pnlibrary.core.runtime.details

import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginMetadata
import org.pnfolder.pnlibrary.api.remote.DenyAction
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.lifecycle.runtimeTaskScope
import org.pnfolder.pnlibrary.core.remote.PolicyDeniedException
import org.pnfolder.pnlibrary.core.remote.RemotePolicyMonitor
import org.pnfolder.pnlibrary.core.remote.RemotePolicyNoticeRenderer
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import java.time.Duration
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

    /** How long server start may wait for the policy before the policy's error action applies. */
    private val STARTUP_TIMEOUT: Duration = Duration.ofSeconds(20)

    /**
     * Checks the policy now, before the plugin reports itself enabled, so a denied library never shows as running.
     * A denial prints its card, stops plugins that depend on the library and throws [PolicyDeniedException]; the
     * checks that follow are scheduled once the server is ready.
     */
    fun start(
        library: PnLibrary,
        platform: PlatformAdapter,
        policy: RemotePolicySettings,
        isClosed: () -> Boolean,
        closeRuntime: () -> Unit,
    ) {
        val owner = library.owner
        val logger = library.logging.logger(owner, "remote-policy")
        val details = platform.ownerDetails(owner)
        val metadata = PluginMetadata(
            ModuleId.of(PnLibraryIdentity.PRODUCT_ID), details["name"] ?: PnLibraryIdentity.NAME, library.version,
            details["authors"] ?: PnLibraryIdentity.ORGANIZATION, platform.type, platform.implementationName,
            System.getProperty("java.version", "unknown"), JavaRuntime.feature,
        )
        val monitor = RemotePolicyMonitor(platform, platform, RemotePolicyNoticeRenderer(platform))
        val startup = monitor.checkAtStartup(owner, metadata, policy, logger, STARTUP_TIMEOUT)
        if (startup == null) {
            val guarded = try { platform.disableDependents(owner) } catch (_: Throwable) { false }
            if (!guarded) runCatchingNonFatal { platform.disableOwner(owner) }
            throw PolicyDeniedException("${metadata.name} ${metadata.version}")
        }
        val started = AtomicBoolean(false)
        platform.whenServerReady(Runnable {
            if (isClosed() || !started.compareAndSet(false, true)) return@Runnable
            try {
                monitor.start(
                    owner = owner,
                    metadata = metadata,
                    policy = policy,
                    tasks = library.runtimeTaskScope(),
                    isClosed = isClosed,
                    logger = logger,
                    closeModule = closeRuntime,
                    closePlugin = closeRuntime,
                    startupCheck = startup,
                )
            } catch (error: Throwable) {
                // A configured policy must not silently disappear if native setup fails.
                try {
                    logger.error("Could not start pnLibrary policy", error)
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
