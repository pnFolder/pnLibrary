package org.pnfolder.pnlibrary.currency;

import org.pnfolder.pnlibrary.currency.storage.FileCurrencyStorage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.currency.CurrencyCommandSettings;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

class CurrencyBuildersTest {
    @Test
    void managedDefinitionRequiresStorage() {
        ManagedCurrencyDefinition definition = new ManagedCurrencyDefinition(PluginId.of("test"), "coins");

        IllegalStateException error = assertThrows(IllegalStateException.class, definition::provider);

        assertEquals("Managed currency test:coins requires storage", error.getMessage());
    }

    @Test
    void managedDefinitionExposesConfiguredCommandSettings(@TempDir Path directory) throws Exception {
        ManagedCurrencyDefinition definition = new ManagedCurrencyDefinition(PluginId.of("test"), "coins");
        definition.ownedStorage(new FileCurrencyStorage(directory.resolve("coins.json"), 100));
        definition.commands(commands -> {
            commands.enabled(false);
            commands.permissionPrefix(" custom.currency ");
            commands.confirmationTimeout(Duration.ofSeconds(120));
        });

        CurrencyProvider provider = definition.provider();
        CurrencyCommandSettings settings = provider.extension(CurrencyCommandSettings.class);

        assertEquals(false, settings.isEnabled());
        assertEquals("custom.currency", settings.getPermissionPrefix());
        assertEquals(Duration.ofSeconds(120), settings.getConfirmationTimeout());
        assertEquals(new CurrencyCommandSettings(), settings.toBuilder().enabled(true).permissionPrefix(null)
                .confirmationTimeout(Duration.ofSeconds(60)).build());
        ((AutoCloseable) provider).close();
    }

    @Test
    void currencyNamesAreNormalizedConsistently() {
        assertEquals("server.coins", CurrencyNames.normalize(" Server.Coins "));
        assertThrows(IllegalArgumentException.class, () -> CurrencyNames.normalize("bad name"));
        assertEquals("test:server.coins",
                new CurrencyKey(PluginId.of("test"), CurrencyNames.normalize(" Server.Coins ")).toString());
    }
}
