package org.pnfolder.pnlibrary.bukkit

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.updates.UpdateService
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender
import org.pnfolder.pnlibrary.bukkit.commands.BukkitControlCommand
import java.util.UUID
import java.util.logging.Logger

class PlayerCommandReplyTest {
    private fun plugin(): Plugin = mock(Plugin::class.java).also {
        `when`(it.logger).thenReturn(Logger.getAnonymousLogger())
        `when`(it.server).thenReturn(mock(Server::class.java))
    }

    @Test
    fun `Paper command reply targets the actual player and preserves click events`() {
        val player = mock(Player::class.java, withSettings().extraInterfaces(Audience::class.java))
        val provider = mock(BukkitAudiences::class.java)
        val service = BukkitAudienceService(plugin(), provider)
        val message = Component.text("[История]").clickEvent(ClickEvent.runCommand("/pn updates history"))
        try {
            service.player(player).sendMessage(message)
            verify(player as Audience).sendMessage(message)
            verifyNoInteractions(provider)
            verify(player, never()).sendMessage(anyString())
        } finally { service.close() }
    }

    @Test
    fun `empty UUID audience does not silently swallow a legacy player reply`() {
        val player = mock(Player::class.java)
        val id = UUID.randomUUID()
        `when`(player.uniqueId).thenReturn(id)
        val provider = mock(BukkitAudiences::class.java)
        `when`(provider.player(id)).thenReturn(Audience.empty())
        val service = BukkitAudienceService(plugin(), provider)
        try {
            service.player(player).sendMessage(Component.text("Каталог pnLibrary"))
            verify(player).sendMessage("Каталог pnLibrary")
        } finally { service.close() }
    }

    @Test
    fun `root updates list and available commands reply to the invoking player even for empty catalog`() {
        val plugin = plugin()
        val player = mock(Player::class.java, withSettings().extraInterfaces(Audience::class.java))
        val provider = mock(BukkitAudiences::class.java)
        val service = BukkitAudienceService(plugin, provider)
        val library = mock(PnLibrary::class.java)
        val updates = mock(UpdateService::class.java)
        `when`(library.updates).thenReturn(updates)
        `when`(updates.all()).thenReturn(emptyList())
        val definition = BukkitControlCommand(plugin, library, mock(CurrencyProviderRegistry::class.java)).definition()
        val root = definition.root
        val catalog = root.children.single { it.name == "updates" }
        val sender = BukkitCommandSender(player, requireNotNull(BukkitAudienceAdapter(service).sender(player)))
        val context = CommandContext(sender, emptyList(), "pnlibrary:pn", "")
        try {
            listOf(root, catalog, catalog.children.single { it.name == "list" }, catalog.children.single { it.name == "available" }).forEach { node ->
                node.execution.execute(context).toCompletableFuture().join()
            }
            val messages = ArgumentCaptor.forClass(Component::class.java)
            verify(player as Audience, atLeast(12)).sendMessage(messages.capture())
            assertTrue(messages.allValues.any { component -> hasClick(component, "/pn updates history") })
            verifyNoInteractions(provider)
        } finally { service.close() }
    }

    private fun hasClick(component: Component, command: String): Boolean =
        component.clickEvent()?.value() == command || component.children().any { hasClick(it, command) }
}
