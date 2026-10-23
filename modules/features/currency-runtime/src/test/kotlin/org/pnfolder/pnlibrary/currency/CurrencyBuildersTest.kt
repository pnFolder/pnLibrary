package org.pnfolder.pnlibrary.currency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.currency.CurrencyCommandSettings
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.nio.file.Path
import java.time.Duration
import java.util.function.Consumer

class CurrencyBuildersTest {
    @Test
    fun `managed definition requires storage`() {
        val definition = ManagedCurrencyDefinition(PluginId.of("test"), "coins")

        val error = assertThrows(IllegalStateException::class.java, definition::provider)

        assertEquals("Managed currency test:coins requires storage", error.message)
    }

    @Test
    fun `managed definition exposes configured command settings`(@TempDir directory: Path) {
        val definition = ManagedCurrencyDefinition(PluginId.of("test"), "coins")
        definition.ownedStorage(FileCurrencyStorage(directory.resolve("coins.json"), 100))
        definition.commands(Consumer { commands ->
            commands.enabled(false)
            commands.permissionPrefix(" custom.currency ")
            commands.confirmationTimeout(Duration.ofSeconds(120))
        })

        val provider = definition.provider()
        val settings = provider.extension(CurrencyCommandSettings::class.java)!!

        assertEquals(false, settings.enabled)
        assertEquals("custom.currency", settings.permissionPrefix)
        assertEquals(Duration.ofSeconds(120), settings.confirmationTimeout)
        assertEquals(CurrencyCommandSettings(), settings.copy(enabled = true, permissionPrefix = null, confirmationTimeout = Duration.ofSeconds(60)))
        (provider as AutoCloseable).close()
    }

    @Test
    fun `currency names are normalized consistently`() {
        assertEquals("server.coins", normalizeCurrencyName(" Server.Coins "))
        assertThrows(IllegalArgumentException::class.java) { normalizeCurrencyName("bad name") }
        assertEquals(
            "test:server.coins",
            CurrencyKey(PluginId.of("test"), normalizeCurrencyName(" Server.Coins ")).toString(),
        )
    }
}
