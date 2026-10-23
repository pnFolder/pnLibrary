package org.pnfolder.pnlibrary.bukkit.inventory

import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class MenuServiceImplTest {
    private val owner = mock<Plugin>().also { `when`(it.isEnabled).thenReturn(true) }
    private val host = mock<Plugin>().also { plugin ->
        val server = mock<Server>()
        `when`(plugin.server).thenReturn(server)
        `when`(server.pluginManager).thenReturn(mock<PluginManager>())
    }
    private val region = ThreadLocal<UUID>()
    private val queued = ConcurrentLinkedQueue<Pair<Player, () -> Unit>>()
    private val execution = object : MenuExecution {
        override fun requireOwner(player: Player) { check(region.get() == player.uniqueId) }
        override fun execute(player: Player, action: () -> Unit) {
            if (region.get() == player.uniqueId) action() else queued.add(player to action)
        }
    }
    private val service = MenuServiceImpl(host, mock<TaskScope>(), execution) { menu, holder ->
        mock<Inventory>().also { `when`(it.holder).thenReturn(holder); `when`(it.size).thenReturn(menu.size) }
    }
    private fun menu(renderer: MenuRenderer? = null) = Menu("test", 1, MenuType.CHEST,
        emptyMap(), renderer, null, null, null, true)
    private fun player(): Player = mock<Player>().also { player ->
        `when`(player.uniqueId).thenReturn(UUID.randomUUID())
        val inventory = AtomicReference<Inventory>()
        val view = mock<InventoryView>()
        `when`(player.openInventory).thenReturn(view)
        `when`(view.topInventory).thenAnswer { inventory.get() }
        doAnswer { inventory.set(it.getArgument(0)); view }.`when`(player).openInventory(any(Inventory::class.java))
    }
    private fun <T> on(player: Player, action: () -> T): T {
        region.set(player.uniqueId)
        try { return action() } finally { region.remove() }
    }

    @Test fun `parallel regions share indexes and owner close dispatches each native cleanup`() {
        val players = (1..20).map { player() }
        val pool = Executors.newFixedThreadPool(4)
        try {
            val opened = players.map { player -> pool.submit<MenuSession> { on(player) { service.open(owner, player, menu()) } } }
            opened.forEach { it.get(5, TimeUnit.SECONDS) }
            players.forEach { assertNotNull(service.session(it)) }
            service.close(owner)
            players.forEach { assertNull(service.session(it)); verify(it, never()).closeInventory() }
            assertEquals(players.size, queued.size)
            while (queued.isNotEmpty()) { val (player, action) = queued.remove(); on(player, action) }
            players.forEach { verify(it).closeInventory() }
        } finally { pool.shutdownNow() }
    }

    @Test fun `wrong thread inventory access fails and renderer failure releases the session`() {
        val player = player()
        assertThrows<IllegalStateException> { service.open(owner, player, menu()) }
        on(player) {
            assertThrows<IllegalStateException> { service.open(owner, player, menu(MenuRenderer { error("renderer") })) }
        }
        assertNull(service.session(player))
    }

    @Test fun `off region session close waits for its owning region and is idempotent`() {
        val player = player()
        val session = on(player) { service.open(owner, player, menu()) }
        assertThrows<IllegalStateException> { session.get(0) }
        session.close()
        verify(player, never()).closeInventory()
        val (_, action) = queued.remove()
        on(player, action)
        session.close()
        verify(player).closeInventory()
        assertNull(service.session(player))
    }
}
