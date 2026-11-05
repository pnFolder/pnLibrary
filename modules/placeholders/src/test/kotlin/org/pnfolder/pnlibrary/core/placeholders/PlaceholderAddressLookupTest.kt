package org.pnfolder.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Files

class PlaceholderAddressLookupTest {
    private val hub = PlaceholderHub(TestPlatform)

    private fun scope(key: String, plugin: String, module: String): PlaceholderService =
        hub.scope(PluginId.of(key), false, plugin, module)

    @Test
    fun `a placeholder is found by its full address from another plugin`() {
        val clans = scope("m-clans-war-1", "pnclans", "war")
        clans.register("score", Int::class.javaObjectType) { it.resolve { 7 }.access(org.pnfolder.pnlibrary.api.placeholders.PlaceholderAccess.shared()) }
        val other = scope("m-shop-main-1", "pnshop", "main")

        assertTrue(other.contains("pnclans/war:score"))
        assertEquals(7, other.resolve("pnclans/war:score").toCompletableFuture().get())
        assertFalse(other.contains("pnclans/main:score"))
    }

    @Test
    fun `a pattern placeholder is found by address and keeps its parameters`() {
        val clans = scope("m-clans-war-1", "pnclans", "war")
        clans.register("player.{name}", String::class.java) { builder ->
            builder.resolve { request -> request.parameters.getValue("name") }
            builder.access(org.pnfolder.pnlibrary.api.placeholders.PlaceholderAccess.shared())
        }

        assertEquals("Alex", clans.resolve("pnclans/war:player.Alex").toCompletableFuture().get())
    }

    @Test
    fun `a closed placeholder leaves the address index`() {
        val clans = scope("m-clans-war-1", "pnclans", "war")
        val registration = clans.register("score", String::class.java) { it.resolve { "x" } }
        assertTrue(clans.contains("pnclans/war:score"))

        registration.close()

        assertFalse(clans.contains("pnclans/war:score"))
    }

    @Test
    fun `two modules may use the same name but one module cannot register it twice`() {
        scope("m-clans-war-1", "pnclans", "war").register("score", String::class.java) { it.resolve { "a" } }
        scope("m-clans-bank-1", "pnclans", "bank").register("score", String::class.java) { it.resolve { "b" } }

        assertTrue(hub.scope(PluginId.of("m-x"), false).contains("pnclans/bank:score"))
        assertThrows(IllegalArgumentException::class.java) {
            scope("m-clans-war-2", "pnclans", "war").register("score", String::class.java) { it.resolve { "c" } }
        }
    }

    private object TestPlatform : PlatformAdapter {
        override val type = PlatformType.BUKKIT
        override val dataFolder = Files.createTempDirectory("pnlibrary-placeholder-test")
        override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) = task.run()
        override fun executeReply(recipient: Any, task: Runnable) = task.run()
        override fun close() = Unit
    }
}
