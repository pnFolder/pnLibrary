package org.pnfolder.pnlibrary.bukkit.integration;

import java.util.function.BiFunction;

import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.currency.CurrencyApi;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistration;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.common.lang.Failures;

/**
 * Exposes an external economy plugin (Vault, PlayerPoints, ...) as a pnLibrary currency.
 *
 * <p>The provider factory returns {@code null} when the plugin is present but its economy service is not ready, in
 * which case the integration stays disconnected and is retried on the next event.
 */
final class CurrencyIntegration implements BukkitIntegration {
    private final IntegrationContext context;
    private final String pluginName;
    private final String providerId;
    private final String currencyName;
    private final BiFunction<Plugin, PnLogger, CurrencyProvider> createProvider;
    private CurrencyRegistration registration;

    CurrencyIntegration(IntegrationContext context, String pluginName, String providerId, String currencyName,
                        BiFunction<Plugin, PnLogger, CurrencyProvider> createProvider) {
        this.context = context;
        this.pluginName = pluginName;
        this.providerId = providerId;
        this.currencyName = currencyName;
        this.createProvider = createProvider;
    }

    @Override
    public String getPluginName() {
        return pluginName;
    }

    @Override
    public void connect() {
        if (registration != null || !context.getPlugin().getServer().getPluginManager().isPluginEnabled(pluginName)) {
            return;
        }
        PnLogger log = context.getRuntime().getLibrary().getLogging().logger(context.getPlugin(), pluginName);
        CurrencyProvider provider = createProvider.apply(context.getPlugin(), log);
        if (provider == null) {
            return;
        }
        registration = CurrencyApi.providers(context.getRuntime().getLibrary())
            .register(PluginId.of(providerId), currencyName, provider);
        context.getRuntime().getLibrary().getLogging().logger(context.getPlugin(), "pnLibrary")
            .info(pluginName + " currency connected as " + providerId + ":" + currencyName);
    }

    @Override
    public void close() {
        CurrencyRegistration active = registration;
        if (active != null) {
            Failures.runCatchingNonFatal(() -> {
                active.close();
                return null;
            }).onFailure(error -> context.getRuntime().getLibrary().getLogging()
                .logger(context.getPlugin(), "pnLibrary").error(pluginName + " currency did not close cleanly", error));
        }
        registration = null;
    }
}
