package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.network.NetworkProxy;
import org.pnfolder.pnlibrary.spi.audience.PlatformAudienceAdapter;
import org.pnfolder.pnlibrary.spi.audience.UnsupportedPlatformAudienceAdapter;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter;
import org.pnfolder.pnlibrary.spi.commands.UnsupportedPlatformCommandAdapter;
import org.pnfolder.pnlibrary.spi.metrics.NoopMetricsFactory;
import org.pnfolder.pnlibrary.spi.metrics.PlatformMetricsFactory;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter;
import org.pnfolder.pnlibrary.spi.tasks.UnsupportedPlatformTaskAdapter;

import java.nio.file.Path;

/**
 * Runtime-only boundary between the shared engine and a native server platform.
 *
 * <p>Implementations translate native ownership, logging, diagnostics, scheduling, and metrics into
 * contracts understood by {@code pnlibrary-core}. Consumer plugins must use {@code PnLibrary} instead of this
 * SPI; no source or binary compatibility is promised to ordinary consumers.
 *
 * <p>Unless a method says otherwise, an adapter must tolerate calls from pnLibrary worker threads.
 * {@link #close()} must be idempotent and prevent newly submitted platform work where practical.
 */
public interface PlatformAdapter extends PlatformShutdown, PlatformIdentity, PlatformExecution, PlatformBinding,
        PlatformReadiness, PlatformOwnership, PlatformDiagnostics, PlatformLogObservation, PlatformLogging {
    /** Durable data root required by the shared runtime. */
    @NotNull
    Path getDataFolder();

    /** Factory for native metrics; no-op by default. */
    @NotNull
    default PlatformMetricsFactory getMetricsFactory() {
        return NoopMetricsFactory.INSTANCE;
    }

    /** Native command registration; unsupported by default. */
    @NotNull
    default PlatformCommandAdapter getCommandAdapter() {
        return UnsupportedPlatformCommandAdapter.INSTANCE;
    }

    /** Native message delivery; unsupported by default. */
    @NotNull
    default PlatformAudienceAdapter getPlayerAdapter() {
        return UnsupportedPlatformAudienceAdapter.INSTANCE;
    }

    /** Native task scheduling; unsupported by default. */
    @NotNull
    default PlatformTaskAdapter getTaskAdapter() {
        return UnsupportedPlatformTaskAdapter.INSTANCE;
    }

    /** Proxy layer; null on game-server platforms. */
    @Nullable
    default NetworkProxy getNetworkProxy() {
        return null;
    }
}
