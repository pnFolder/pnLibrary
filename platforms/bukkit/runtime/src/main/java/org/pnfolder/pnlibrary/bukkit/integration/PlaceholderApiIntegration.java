package org.pnfolder.pnlibrary.bukkit.integration;

import org.pnfolder.pnlibrary.bukkit.placeholders.PlaceholderApiAdapter;

/** Registers pnLibrary placeholders with PlaceholderAPI while that plugin is enabled. */
final class PlaceholderApiIntegration implements BukkitIntegration {
    private final IntegrationContext context;
    private AutoCloseable registration;

    PlaceholderApiIntegration(IntegrationContext context) {
        this.context = context;
    }

    @Override
    public String getPluginName() {
        return "PlaceholderAPI";
    }

    @Override
    public void connect() {
        if (registration != null || !context.getPlugin().getServer().getPluginManager().isPluginEnabled(getPluginName())) {
            return;
        }
        registration = context.getRuntime().getLibrary().getPlaceholderAdapters()
            .register(new PlaceholderApiAdapter(context.getPlugin()));
        context.getRuntime().getLibrary().getLogging().logger(context.getPlugin(), "pnLibrary")
            .info("PlaceholderAPI integration connected");
    }

    @Override
    public void close() {
        if (registration != null) {
            try {
                registration.close();
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }
        registration = null;
    }
}
