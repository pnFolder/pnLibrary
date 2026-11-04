package org.pnfolder.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.placeholders.tree.formats
import org.pnfolder.pnlibrary.api.placeholders.tree.from
import org.pnfolder.pnlibrary.api.placeholders.tree.list
import org.pnfolder.pnlibrary.api.placeholders.tree.none
import org.pnfolder.pnlibrary.api.placeholders.tree.placeholders
import org.pnfolder.pnlibrary.api.placeholders.tree.table
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Files
import java.util.UUID

class PlaceholderTreeTest {
    private val issues = mutableListOf<String>()
    private val hub = PlaceholderHub(TestPlatform) { issues += it }

    private fun module(plugin: String, id: String): Pair<ModuleContext, PlaceholderService> {
        val service = hub.scope(PluginId.of("m-$plugin-$id"), true, plugin, id)
        val module = mock(ModuleContext::class.java)
        `when`(module.placeholders).thenReturn(service)
        `when`(module.id).thenReturn(ModuleId.of(id))
        return module to service
    }

    private fun read(service: PlaceholderService, text: String, player: UUID? = null): Any? =
        service.resolve(text, player).toCompletableFuture().get()

    @Test
    fun `a format of one plugin can be used by another by its address`() {
        val (war, _) = module("pnclans", "war")
        war.formats {
            format<Int>("rank") { "#$it" }
            format<Int>("pad0") { n, args -> n.toString().padStart(args.first().toInt(), '0') }
        }
        val (top, service) = module("pntop", "main")
        top.placeholders { int("place") { value { 3 } } }

        assertEquals("#3", read(service, "pntop/main:place|pnclans/war:rank"))
        assertEquals("003", read(service, "pntop/main:place|pnclans/war:pad0:3"))
        assertEquals(3, read(service, "pntop/main:place|pnclans/war:missing"))
    }

    data class Clan(val name: String, val members: Int, val open: Boolean)

    @Test
    fun `a tree becomes placeholders named by their paths and arguments are typed`() {
        val (war, service) = module("pnclans", "war")
        war.placeholders {
            int("score") {
                value { 7 }
                player("who") {
                    value { arg.text.length }
                    int("kills") { value { 3 } }
                }
                int("top") {
                    value { 1 }
                    param("place", int, range = 1..100) { value { arg * 10 } }
                }
            }
        }

        assertEquals(7, read(service, "pnclans/war:score"))
        assertEquals(4, read(service, "pnclans/war:score.Alex"))
        assertEquals(3, read(service, "pnclans/war:score.Alex.kills"))
        assertEquals(1, read(service, "pnclans/war:score.top"))
        assertEquals(30, read(service, "pnclans/war:score.top.3"))
        assertNull(read(service, "pnclans/war:score.top.500"))
    }

    @Test
    fun `a branch without a value only groups`() {
        val (war, service) = module("pnclans", "war")
        war.placeholders { int("score") { int("kills") { value { 3 } } } }

        assertNull(read(service, "pnclans/war:score"))
        assertEquals(3, read(service, "pnclans/war:score.kills"))
    }

    @Test
    fun `formats apply by level and the text can override or switch them off`() {
        val (war, service) = module("pnclans", "war")
        war.placeholders {
            format("group")
            int("score") { value { 1234567 } }
            int("plain") { format(none); value { 1234567 } }
            text("name") { format("upper"); value { "wolf" } }
        }

        assertEquals("1 234 567", read(service, "pnclans/war:score"))
        assertEquals(1234567, read(service, "pnclans/war:plain"))
        assertEquals("WOLF", read(service, "pnclans/war:name"))
        assertEquals(1234567, read(service, "pnclans/war:score|raw"))
        assertEquals("1234567", read(service, "pnclans/war:score|number"))
    }

    @Test
    fun `a module can add its own formats`() {
        val (war, service) = module("pnclans", "war")
        war.formats { format<Int>("rank") { "#$it" } }
        war.placeholders { int("score") { value { 3 } } }

        assertEquals("#3", read(service, "pnclans/war:score|rank"))
    }

    @Test
    fun `branches are made from the properties of a class`() {
        val (war, service) = module("pnclans", "war")
        war.placeholders {
            from<Clan, Unit>("clan") { Clan("wolves", 12, true) }.only(Clan::name, Clan::members).rename(Clan::members, "size")
        }

        assertEquals("wolves", read(service, "pnclans/war:clan.name"))
        assertEquals(12, read(service, "pnclans/war:clan.size"))
        assertNull(read(service, "pnclans/war:clan.open"))
    }

    @Test
    fun `table and list make one branch per key and place`() {
        val (war, service) = module("pnclans", "war")
        war.placeholders {
            table("price", { listOf("sword", "apple") }) { key -> key.length }
            list("top", { 3 }) { place -> place * 2 }
        }

        assertEquals(5, read(service, "pnclans/war:price.sword"))
        assertNull(read(service, "pnclans/war:price.bow"))
        assertEquals(6, read(service, "pnclans/war:top.3"))
        assertNull(read(service, "pnclans/war:top.4"))
    }

    @Test
    fun `access rules close a branch to the plugins they name and a denial wins`() {
        val (war, _) = module("pnclans", "war")
        val (_, shop) = module("pnshop", "main")
        val (_, spy) = module("pnspy", "main")
        war.placeholders {
            int("open") { value { 1 } }
            int("private") { access { denyAll() }; value { 2 } }
            int("some") { access { allowOnly("m-pnshop-main") }; value { 3 } }
            int("notspy") { access { deny("m-pnspy-main") }; value { 4 } }
        }

        assertEquals(1, read(shop, "pnclans/war:open"))
        assertNull(runCatching { read(shop, "pnclans/war:private") }.getOrNull())
        assertEquals(3, read(shop, "pnclans/war:some"))
        assertNull(runCatching { read(spy, "pnclans/war:some") }.getOrNull())
        assertEquals(4, read(shop, "pnclans/war:notspy"))
        assertNull(runCatching { read(spy, "pnclans/war:notspy") }.getOrNull())
    }

    @Test
    fun `inside a plugin the short name finds the placeholder in any of its modules`() {
        val (war, warService) = module("pnclans", "war")
        val (bank, _) = module("pnclans", "bank")
        war.placeholders { int("score") { value { 7 } } }
        bank.placeholders { int("money") { value { 9 } } }

        assertEquals(7, read(warService, "score"))
        assertEquals(9, read(warService, "money"))
    }

    @Test
    fun `render keeps what is not found, reports it once and survives a denied placeholder`() {
        val (war, service) = module("pnclans", "war")
        val (_, shop) = module("pnshop", "main")
        war.placeholders {
            int("score") { value { 7 } }
            int("private") { access { denyAll() }; value { 2 } }
        }

        val text = shop.render("a {pnclans/war:score} b {pnclans/war:nothing} c {pnclans/war:private}").toCompletableFuture().get()
        shop.render("{pnclans/war:nothing}").toCompletableFuture().get()

        assertEquals("a 7 b {pnclans/war:nothing} c {pnclans/war:private}", text)
        assertEquals(1, issues.count { it.contains("nothing") })
        assertTrue(issues.any { it.contains("may not read") })
        assertEquals(7, read(service, "score"))
    }

    @Test
    fun `a prefix belongs to the first plugin that publishes under it`() {
        val (war, _) = module("pnclans", "war")
        val (other, _) = module("pnshop", "main")

        war.placeholders(papi = "clans") { int("score") { value { 1 } } }
        other.placeholders(papi = "clans") { int("price") { value { 2 } } }

        assertTrue(issues.any { it.contains("prefix 'clans' already belongs to pnclans") })
    }

    private object TestPlatform : PlatformAdapter {
        override val type = org.pnfolder.pnlibrary.api.platform.PlatformType.BUKKIT
        override val dataFolder = Files.createTempDirectory("pnlibrary-placeholder-tree-test")
        override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) = task.run()
        override fun executeReply(recipient: Any, task: Runnable) = task.run()
        override fun close() = Unit
    }
}
