package ru.privatenull.pnlibrary.core.currency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.currency.CurrencyCommandSettings
import ru.privatenull.pnlibrary.api.currency.CurrencyKey
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.nio.file.Path
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
            commands.confirmationTimeoutSeconds(120)
        })

        val provider = definition.provider()
        val settings = provider.extension(CurrencyCommandSettings::class.java)!!

        assertEquals(false, settings.enabled)
        assertEquals("custom.currency", settings.permissionPrefix)
        assertEquals(120, settings.confirmationTimeoutSeconds)
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
