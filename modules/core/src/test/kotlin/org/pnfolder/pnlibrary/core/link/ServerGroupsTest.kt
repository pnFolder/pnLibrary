package org.pnfolder.pnlibrary.core.link

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

class ServerGroupsTest {
    private fun report(vararg plugins: LinkPlugin) = LinkReport("Paper", "0.0.1", 0, plugins.toList(), Instant.now())

    @Test
    fun `groups are kept in the file and edited by name`(@TempDir folder: Path) {
        val groups = ServerGroups { folder.resolve(ServerGroups.FILE) }

        assertTrue(groups.create("grief"))
        assertFalse(groups.create("GRIEF"))
        assertFalse(groups.create("create"))
        assertTrue(groups.edit("Grief") { it.copy(servers = it.servers + "g1", plugins = it.plugins + "shop") })

        val again = ServerGroups { folder.resolve(ServerGroups.FILE) }.get("grief")!!
        assertEquals("grief", again.name)
        assertEquals(setOf("g1"), again.servers)
        assertEquals(setOf("shop"), again.plugins)
        assertTrue(groups.delete("grief"))
        assertNull(groups.get("grief"))
        assertFalse(groups.edit("grief") { it })
    }

    @Test
    fun `one server of a group can skip a plugin or carry one more`(@TempDir folder: Path) {
        val groups = ServerGroups { folder.resolve(ServerGroups.FILE) }
        groups.create("lobby")
        groups.create("event")
        groups.edit("lobby") { it.copy(servers = setOf("l1", "l2"), plugins = setOf("shop", "chat")) }
        groups.edit("event") { it.copy(servers = setOf("l1"), plugins = setOf("quiz")) }
        groups.edit("lobby") { it.with("l2", "chat", false).with("l2", "parkour", true) }

        val lobby = ServerGroups { folder.resolve(ServerGroups.FILE) }.get("lobby")!!
        assertEquals(setOf("shop", "chat"), lobby.wanted("l1"))
        assertEquals(setOf("shop", "parkour"), lobby.wanted("l2"))
        assertTrue(lobby.customized("l2"))
        assertFalse(lobby.customized("l1"))
        assertEquals(listOf("event", "lobby"), groups.of("L1").map { it.name })

        groups.edit("lobby") { it.with("l2", "chat", true).reset("l2") }
        assertEquals(setOf("shop", "chat"), groups.get("lobby")!!.wanted("l2"))
        assertFalse(groups.get("lobby")!!.customized("l2"))
    }

    @Test
    fun `a skipped plugin is not missing on that server`() {
        val group = ServerGroups.Group("lobby", setOf("l1"), setOf("shop", "chat")).with("l1", "chat", false)
        val servers = listOf(NetworkServer("l1", true, report(LinkPlugin("shop", "1", null, "UP_TO_DATE"))))

        val state = ServerGroups { null }.compare(group, servers).single()

        assertTrue(state.ok)
        assertTrue(state.customized)
    }

    @Test
    fun `a server is compared with what the group asks for`(@TempDir folder: Path) {
        val groups = ServerGroups { folder.resolve(ServerGroups.FILE) }
        val group = ServerGroups.Group("grief", setOf("g1", "g2", "g3"), setOf("shop", "warps"))
        val servers = listOf(
            NetworkServer("g1", true, report(LinkPlugin("shop", "1", null, "UP_TO_DATE"), LinkPlugin("warps", "1", null, "UP_TO_DATE"))),
            NetworkServer("g2", true, report(LinkPlugin("shop", "1", "2", "UPDATE_AVAILABLE"))),
        )

        val states = groups.compare(group, servers).associateBy { it.server }

        assertTrue(states.getValue("g1").ok)
        assertEquals(listOf("warps"), states.getValue("g2").missing)
        assertEquals(listOf("shop"), states.getValue("g2").outdated)
        assertNull(states.getValue("g3").known)
        assertFalse(states.getValue("g3").ok)
    }
}
