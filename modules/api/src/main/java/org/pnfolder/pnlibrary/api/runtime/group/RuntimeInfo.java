package org.pnfolder.pnlibrary.api.runtime.group;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.network.NetworkProxy;
import org.pnfolder.pnlibrary.api.platform.PlatformProvider;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;

/**
 * Which library is running and where: its version, settings, platform and proxy.
 *
 * <pre>{@code
 * if (!PnLibraryProvider.get().getRuntime().isAtLeastVersion("0.1.0")) {
 *     return;
 * }
 * }</pre>
 */
@ApiStatus.NonExtendable
public interface RuntimeInfo {
    /** Version of the installed runtime. */
    @NotNull
    String getVersion();

    /** Returns {@code true} when this runtime is not older than {@code minimumVersion}. */
    boolean isAtLeastVersion(@NotNull String minimumVersion);

    /** Native platform plugin instance that owns this runtime. */
    @NotNull
    Object getOwner();

    /** Validated configuration of the installed runtime. */
    @NotNull
    PnLibraryConfig getConfiguration();

    /** Type-safe native API for the active Bukkit, Bungee, or Velocity runtime. */
    @NotNull
    PlatformProvider getPlatforms();

    /** The proxy layer; null when running on a game server instead of a proxy. */
    @Nullable
    NetworkProxy getProxy();

    /** Whether the runtime has begun closing and no longer accepts normal work. */
    boolean isClosed();
}
