package org.pnfolder.pnlibrary.bungee;

import java.util.Objects;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.runtime.ProxyLifecycle;

/**
 * Starts and stops the shared runtime on BungeeCord. It is public with one public constructor because
 * {@code PnLibraryBungeePlugin} creates it by reflection after the runtime libraries are loaded.
 */
public final class BungeeRuntime {
    private final ProxyLifecycle lifecycle = new ProxyLifecycle();
    private final BungeeStartup startup;

    /** Creates the runtime of the pnLibrary plugin {@code plugin}. */
    public BungeeRuntime(@NotNull Plugin plugin) {
        this.startup = new BungeeStartup(Objects.requireNonNull(plugin, "plugin"));
    }

    /** Starts the runtime; a failure stops what already started and is rethrown. */
    public void start() {
        lifecycle.start(startup);
    }

    /** Closes everything that started; the first failure is thrown after all steps ran. */
    public void stop() {
        lifecycle.close();
    }
}
