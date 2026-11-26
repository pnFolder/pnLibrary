package org.pnfolder.pnlibrary.core.runtime.details;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata;
import org.pnfolder.pnlibrary.api.remote.DenyAction;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;
import org.pnfolder.pnlibrary.core.remote.PolicyDeniedException;
import org.pnfolder.pnlibrary.core.remote.RemotePolicyMonitor;
import org.pnfolder.pnlibrary.core.remote.StartupCheck;
import org.pnfolder.pnlibrary.core.remote.RemotePolicyNoticeRenderer;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;

/** Validates the remote policy the library applies to itself. */
public final class RuntimeRemotePolicy {
    private RuntimeRemotePolicy() {
    }

    public static @NotNull RemotePolicySettings validated(@NotNull RemotePolicySettings policy) {
        if (policy.getOnDeny() != DenyAction.DISABLE_PLUGIN) {
            throw new IllegalArgumentException("pnLibrary policy must use DISABLE_PLUGIN: the library is not a consumer module");
        }
        return RemotePolicySettings.builder().source(policy.getSource()).checkEvery(policy.getCheckEvery())
                .onDeny(policy.getOnDeny()).onError(policy.getOnError()).values(policy.getValues())
                .startupTimeout(policy.getStartupTimeout())
                .build();
    }

    /**
     * Checks the policy now, before the plugin reports itself enabled, so a denied library never shows as running.
     * A denial prints its card, stops plugins that depend on the library and throws {@link PolicyDeniedException}; the
     * checks that follow are scheduled once the server is ready.
     */
    public static void start(@NotNull PnLibrary library, @NotNull PlatformAdapter platform, @NotNull RemotePolicySettings policy,
                             @NotNull BooleanSupplier isClosed, @NotNull Runnable closeRuntime) {
        Object owner = library.getOwner();
        PnLogger logger = library.getLogging().logger(owner, "remote-policy");
        PluginMetadata metadata = metadata(library, platform, owner);
        RemotePolicyMonitor monitor = new RemotePolicyMonitor(platform, platform, new RemotePolicyNoticeRenderer(platform));
        StartupCheck startup = monitor.checkAtStartup(owner, metadata, policy, logger, policy.getStartupTimeout());
        if (startup == null) {
            denyLibrary(platform, owner, logger);
            throw new PolicyDeniedException(metadata.getName() + " " + metadata.getVersion());
        }
        AtomicBoolean started = new AtomicBoolean(false);
        platform.whenServerReady(() -> {
            if (isClosed.getAsBoolean() || !started.compareAndSet(false, true)) {
                return;
            }
            startChecks(library, platform, policy, isClosed, closeRuntime, metadata, monitor, startup, logger);
        });
    }

    private static PluginMetadata metadata(PnLibrary library, PlatformAdapter platform, Object owner) {
        Map<String, String> details = platform.ownerDetails(owner);
        String name = details.get("name") != null ? details.get("name") : PnLibraryIdentity.NAME;
        String authors = details.get("authors") != null ? details.get("authors") : PnLibraryIdentity.ORGANIZATION;
        return new PluginMetadata(ModuleId.of(PnLibraryIdentity.PRODUCT_ID), name, library.getVersion(), authors, platform.getType(),
                platform.getImplementationName(), System.getProperty("java.version", "unknown"), JavaRuntime.getFeature());
    }

    /** Falling back to disabling the owner is safe; only fatal JVM errors must not be swallowed here. */
    private static void denyLibrary(PlatformAdapter platform, Object owner, PnLogger logger) {
        boolean guarded = Failures.runCatchingNonFatal(() -> platform.disableDependents(owner))
                .onFailure(error -> logger.error("Could not disable the plugins that depend on pnLibrary after the policy denied it", error))
                .getOrDefault(false);
        if (!guarded) {
            Failures.runCatchingNonFatal(() -> platform.disableOwner(owner))
                    .onFailure(error -> logger.critical("The policy denied pnLibrary but it could not be disabled", error));
        }
    }

    private static void startChecks(PnLibrary library, PlatformAdapter platform, RemotePolicySettings policy, BooleanSupplier isClosed,
                                    Runnable closeRuntime, PluginMetadata metadata, RemotePolicyMonitor monitor,
                                    StartupCheck startup, PnLogger logger) {
        Object owner = library.getOwner();
        try {
            monitor.start(owner, metadata, policy, RuntimeBridge.runtimeTaskScope(library), isClosed, logger, closeRuntime,
                    closeRuntime, startup);
        } catch (Throwable error) {
            // A configured policy must not silently disappear if native setup fails.
            try {
                logger.error("Could not start pnLibrary policy", error);
            } finally {
                try {
                    if (!platform.disableOwner(owner)) {
                        closeRuntime.run();
                    }
                } catch (Throwable disableError) {
                    error.addSuppressed(disableError);
                    closeRuntime.run();
                }
            }
        }
    }
}
