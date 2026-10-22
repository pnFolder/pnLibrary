package org.pnfolder.demo;

import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.bukkit.bootstrap.PnLibraryBootstrap;

/**
 * Starts after pnLibrary when the server has it (softdepend) and installs it in {@code onLoad} when it does not. A
 * second plugin that does the same finds the library already installed and installs nothing.
 */
public final class BootstrapDemoPlugin extends JavaPlugin {
    private boolean ready;
    private DemoRuntime runtime;

    @Override
    public void onLoad() {
        ready = PnLibraryBootstrap.ensureInstalled(this);
    }

    @Override
    public void onEnable() {
        if (ready && getServer().getPluginManager().isPluginEnabled("pnLibrary")) runtime = DemoRuntime.start(this);
        if (runtime == null) getServer().getPluginManager().disablePlugin(this);
    }

    @Override
    public void onDisable() {
        if (runtime != null) runtime.close();
        runtime = null;
    }
}
