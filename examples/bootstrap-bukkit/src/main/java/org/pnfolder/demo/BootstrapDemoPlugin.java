package org.pnfolder.demo;

import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.bukkit.bootstrap.PnLibraryBootstrap;

/** Asks the embedded installer for pnLibrary, whatever version is newest, and stops when it cannot get one. */
public final class BootstrapDemoPlugin extends JavaPlugin {
    private boolean ready;

    @Override
    public void onLoad() {
        ready = PnLibraryBootstrap.ensureInstalled(this);
    }

    @Override
    public void onEnable() {
        if (!ready) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("pnLibrary is running; the demo has what it needs");
    }
}
