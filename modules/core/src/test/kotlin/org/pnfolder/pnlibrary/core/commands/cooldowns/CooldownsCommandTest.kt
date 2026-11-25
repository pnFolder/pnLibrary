package org.pnfolder.pnlibrary.core.commands.cooldowns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.cooldowns.CooldownService
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry
import org.pnfolder.pnlibrary.core.cooldowns.InMemoryCooldowns
import java.time.Duration
import java.util.UUID

class CooldownsCommandTest {
    @Test
    fun `running cooldowns are listed across plugins and modules longest first`() {
        val player = UUID.randomUUID()
        val shop: CooldownService = InMemoryCooldowns().also { it.set(player, "home", Duration.ofSeconds(30)) }
        val clans: CooldownService = InMemoryCooldowns().also { it.set(player, "war", Duration.ofSeconds(90)) }
        val plugins = registry("shop" to shop, "clans" to clans)

        val running = runningCooldowns(plugins, player)

        assertEquals(listOf("clans/main" to "war", "shop/main" to "home"), running.map { it.owner to it.action })
    }

    private fun registry(vararg modules: Pair<String, CooldownService>): PluginRegistry {
        val registrations = modules.map { (plugin, cooldowns) ->
            val module = mock(ModuleContext::class.java)
            `when`(module.id).thenReturn(ModuleId.of("main"))
            `when`(module.cooldowns).thenReturn(cooldowns)
            val registration = mock(PluginRegistration::class.java)
            `when`(registration.id).thenReturn(PluginId.of(plugin))
            `when`(registration.all()).thenReturn(listOf(module))
            registration
        }
        val registry = mock(PluginRegistry::class.java)
        `when`(registry.all()).thenReturn(registrations)
        return registry
    }
}
