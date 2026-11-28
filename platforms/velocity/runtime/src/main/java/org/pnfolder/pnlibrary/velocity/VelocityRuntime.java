package org.pnfolder.pnlibrary.velocity;

import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.runtime.ProxyLifecycle;

/**
 * Velocity runtime behind the generated {@code PnLibraryVelocityPlugin} descriptor class.
 *
 * <p>The descriptor carries Velocity's {@code @Plugin} metadata and forwards lifecycle events here by reflection,
 * so the class and its one constructor are public. {@code owner} is the registered Velocity plugin instance,
 * which pnLibrary uses for identity and diagnostics. The initialize event starts the library; the shutdown event
 * stops it.
 */
public final class VelocityRuntime {
    private final ProxyLifecycle lifecycle = new ProxyLifecycle();
    private final VelocityStartup startup;

    /** Creates the runtime of the plugin instance {@code owner}. */
    public VelocityRuntime(@NotNull Object owner, @NotNull ProxyServer server, @NotNull Path dataDirectory) {
        this.startup = new VelocityStartup(Objects.requireNonNull(owner, "owner"), Objects.requireNonNull(server, "server"),
                Objects.requireNonNull(dataDirectory, "dataDirectory"));
    }

    /** Starts the shared runtime when Velocity announces proxy initialization. */
    public void onInitialize(@NotNull ProxyInitializeEvent event) {
        lifecycle.start(startup);
    }

    /** Closes the runtime before the proxy completes shutdown. */
    public void onShutdown(@NotNull ProxyShutdownEvent event) {
        lifecycle.close();
    }
}
