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
