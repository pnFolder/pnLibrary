package org.pnfolder.pnlibrary.core.link

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.core.link.report.LinkReport
import java.time.Instant

class LinkRegistryTest {
    private fun report(players: Int) = LinkReport("paper", "1.0", players, emptyList(), Instant.EPOCH)

    @Test
    fun `a newer report replaces the old one and lookup ignores case`() {
        val registry = LinkRegistry()
        registry.accept("Lobby", report(1))
        registry.accept("Lobby", report(5))

        assertEquals(5, registry.get("lobby")!!.report.players)
        assertEquals(1, registry.all().size)
        assertNull(registry.get("other"))
    }

    @Test
    fun `servers are listed by name and can be cleared`() {
        val registry = LinkRegistry()
        registry.accept("beta", report(0))
        registry.accept("Alpha", report(0))

        assertEquals(listOf("Alpha", "beta"), registry.all().map { it.name })
        registry.clear()
        assertEquals(0, registry.all().size)
    }
}
