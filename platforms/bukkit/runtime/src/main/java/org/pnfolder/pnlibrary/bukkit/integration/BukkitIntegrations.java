package org.pnfolder.pnlibrary.bukkit.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.bukkit.currency.BukkitCurrencyAdapters;
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost;

/** Connects the optional integrations as their plugins and services appear, and drops them when those go away. */
public final class BukkitIntegrations implements Listener {
    private static final String VAULT_ECONOMY = "net.milkbowl.vault.economy.Economy";

    private final JavaPlugin plugin;
    private List<BukkitIntegration> integrations = new ArrayList<>();

    public BukkitIntegrations(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(PnLibraryRuntimeHost runtime) {
        IntegrationContext context = new IntegrationContext(plugin, runtime);
        integrations = new ArrayList<>(Arrays.<BukkitIntegration>asList(
            new PlaceholderApiIntegration(context),
            new CurrencyIntegration(context, "Vault", "vault", "money", BukkitCurrencyAdapters::vault),
            new CurrencyIntegration(context, "PlayerPoints", "playerpoints", "points", BukkitCurrencyAdapters::playerPoints)));
        connectAll();
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        for (BukkitIntegration integration : integrations) {
            integration.close();
        }
        integrations = new ArrayList<>();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        connectAll();
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        connectAll();
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        if (event.getProvider().getService().getName().equals(VAULT_ECONOMY)) {
            close("Vault");
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        close(event.getPlugin().getName());
    }

    private void connectAll() {
        for (BukkitIntegration integration : integrations) {
            integration.connect();
        }
    }

    private void close(String pluginName) {
        for (BukkitIntegration integration : integrations) {
            if (integration.getPluginName().equalsIgnoreCase(pluginName)) {
                integration.close();
                return;
            }
        }
    }
}
