package org.pnfolder.pnlibrary.core.runtime.platform;

import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.network.NetworkProxy;
import org.pnfolder.pnlibrary.api.network.channel.ServerNetwork;
import org.pnfolder.pnlibrary.spi.audience.PlatformAudienceAdapter;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter;
import org.pnfolder.pnlibrary.spi.metrics.PlatformMetricsFactory;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;
import org.pnfolder.pnlibrary.spi.platform.PlatformBinding;
import org.pnfolder.pnlibrary.spi.platform.diagnostics.PlatformDiagnostics;
import org.pnfolder.pnlibrary.spi.platform.PlatformExecution;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;
import org.pnfolder.pnlibrary.spi.platform.logging.PlatformLogObservation;
import org.pnfolder.pnlibrary.spi.platform.logging.PlatformLogging;
import org.pnfolder.pnlibrary.spi.platform.plugin.PlatformOwnership;
import org.pnfolder.pnlibrary.spi.platform.lifecycle.PlatformReadiness;
import org.pnfolder.pnlibrary.spi.platform.lifecycle.PlatformShutdown;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter;

/** All platform adapters the runtime needs, gathered in one place. */
public final class PlatformRuntimeContext {
    private final PlatformIdentity identity;
    private final PlatformExecution execution;
    private final PlatformReadiness readiness;
    private final PlatformOwnership ownership;
    private final PlatformDiagnostics diagnostics;
    private final PlatformLogObservation logObservation;
    private final PlatformLogging logging;
    private final PlatformMetricsFactory metricsFactory;
    private final PlatformCommandAdapter commandAdapter;
    private final PlatformAudienceAdapter playerAdapter;
    private final PlatformTaskAdapter taskAdapter;
    private final Path dataFolder;
    private final PlatformBinding binding;
    private final PlatformShutdown shutdown;
    private final NetworkProxy networkProxy;
    private final ServerNetwork serverNetwork;

    public PlatformRuntimeContext(PlatformIdentity identity, PlatformExecution execution, PlatformReadiness readiness,
                                  PlatformOwnership ownership, PlatformDiagnostics diagnostics, PlatformLogObservation logObservation,
                                  PlatformLogging logging, PlatformMetricsFactory metricsFactory, PlatformCommandAdapter commandAdapter,
                                  PlatformAudienceAdapter playerAdapter, PlatformTaskAdapter taskAdapter, Path dataFolder,
                                  PlatformBinding binding, PlatformShutdown shutdown, @Nullable NetworkProxy networkProxy,
                                  @Nullable ServerNetwork serverNetwork) {
        this.identity = identity;
        this.execution = execution;
        this.readiness = readiness;
        this.ownership = ownership;
        this.diagnostics = diagnostics;
        this.logObservation = logObservation;
        this.logging = logging;
        this.metricsFactory = metricsFactory;
        this.commandAdapter = commandAdapter;
        this.playerAdapter = playerAdapter;
        this.taskAdapter = taskAdapter;
        this.dataFolder = dataFolder;
        this.binding = binding;
        this.shutdown = shutdown;
        this.networkProxy = networkProxy;
        this.serverNetwork = serverNetwork;
    }

    /** Takes every adapter from one platform adapter, which implements all of them. */
    public static @NotNull PlatformRuntimeContext from(@NotNull PlatformAdapter adapter) {
        return new PlatformRuntimeContext(adapter, adapter, adapter, adapter, adapter, adapter, adapter, adapter.getMetricsFactory(),
                adapter.getCommandAdapter(), adapter.getPlayerAdapter(), adapter.getTaskAdapter(), adapter.getDataFolder(), adapter,
                adapter, adapter.getNetworkProxy(), adapter.getServerNetwork());
    }

    public @NotNull PlatformIdentity getIdentity() {
        return identity;
    }

    public @NotNull PlatformExecution getExecution() {
        return execution;
    }

    public @NotNull PlatformReadiness getReadiness() {
        return readiness;
    }

    public @NotNull PlatformOwnership getOwnership() {
        return ownership;
    }

    public @NotNull PlatformDiagnostics getDiagnostics() {
        return diagnostics;
    }

    public @NotNull PlatformLogObservation getLogObservation() {
        return logObservation;
    }

    public @NotNull PlatformLogging getLogging() {
        return logging;
    }

    public @NotNull PlatformMetricsFactory getMetricsFactory() {
        return metricsFactory;
    }

    public @NotNull PlatformCommandAdapter getCommandAdapter() {
        return commandAdapter;
    }

    public @NotNull PlatformAudienceAdapter getPlayerAdapter() {
        return playerAdapter;
    }

    public @NotNull PlatformTaskAdapter getTaskAdapter() {
        return taskAdapter;
    }

    public @Nullable Path getDataFolder() {
        return dataFolder;
    }

    public @NotNull PlatformBinding getBinding() {
        return binding;
    }

    public @NotNull PlatformShutdown getShutdown() {
        return shutdown;
    }

    public @Nullable NetworkProxy getNetworkProxy() {
        return networkProxy;
    }

    public @Nullable ServerNetwork getServerNetwork() {
        return serverNetwork;
    }

    public @NotNull String getSummaryName() {
        String type = identity.getType().getDisplayName();
        if (identity.getImplementationName().equalsIgnoreCase(type)) {
            return type;
        }
        return type + " / " + identity.getImplementationName();
    }
}
