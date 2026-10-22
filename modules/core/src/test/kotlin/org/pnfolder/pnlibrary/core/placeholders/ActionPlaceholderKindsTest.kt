package org.pnfolder.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.audiences.LibraryAudience
import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.placeholders.ExternalPlaceholderRegistration
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterCapabilities
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterState
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderPublication
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderResolver
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.text.ComponentService
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Files
import java.util.UUID

/** `[value]`, `{placeholder}` and `%external%` read different sources, and formats chain after a bar. */
class ActionPlaceholderKindsTest {
    private val hub = PlaceholderHub(TestPlatform)
    private val service = hub.scope(PluginId.of("shop"), false).also { scope ->
        scope.register("money", Number::class.java) { it.resolve { 1234567.891 } }
        scope.register("rank", String::class.java) { it.resolve { "vip" } }
        scope.register("stars", Int::class.javaObjectType) {
            it.resolve { 3 }
                .format("bar") { value, arguments, _ -> arguments.getOrElse(0) { "*" }.repeat(value) }
                .format("short") { value, _, _ -> "own-$value" }
        }
        scope.register("same", String::class.java) { it.resolve { "from-library" } }
        scope.adapters().register(Bridge)
    }

    private val player = mock(LibraryPlayer::class.java).also {
        `when`(it.uniqueId).thenReturn(UUID.randomUUID())
        `when`(it.name).thenReturn("Alice")
    }

    private fun context() = ActionContext(
        player = player,
        allPlayers = mock(LibraryAudience::class.java),
        components = mock(ComponentService::class.java),
        logger = mock(PnLogger::class.java),
        tasks = mock(TaskScope::class.java),
        values = mapOf("same" to "from-action", "hp" to 3.14159),
        placeholders = service,
    )

    @Test
    fun `each kind of brackets reads its own source`() {
        val text = context().format("[same] {same} %server_tps% {money} [missing] {missing} %missing%")

        assertEquals("from-action from-library 19.9 1234567.891 [missing] {missing} %missing%", text)
    }

    @Test
    fun `the player name is always available`() {
        assertEquals("Alice Alice Alice", context().format("[player] {player_name} %player%"))
    }

    @Test
    fun `formats chain after a bar in values and in placeholders`() {
        val ctx = context()

        assertEquals("1 234 567.891", ctx.format("{money|group}"))
        assertEquals("1.2M", ctx.format("{money|short}"))
        assertEquals("3.14", ctx.format("[hp|number:2]"))
        assertEquals("Vip", ctx.format("{rank|capitalize}"))
        assertEquals("VIP", ctx.format("{rank|upper}"))
    }

    @Test
    fun `a format registered on a placeholder belongs to it alone and wins over a built-in one`() {
        val ctx = context()

        assertEquals("***", ctx.format("{stars|bar}"))
        assertEquals("###", ctx.format("{stars|bar:#}"))
        assertEquals("own-3", ctx.format("{stars|short}"))
        assertEquals("1.2M", ctx.format("{money|short}"))
        assertEquals("1234567.891", ctx.format("{money|bar}"))
    }

    @Test
    fun `a text value skips a format meant for another type`() {
        assertEquals("OWN-3", context().format("{stars|short|upper}"))
        assertEquals("3", context().format("{stars|upper|bar}"))
    }

    @Test
    fun `text that a placeholder produced is not read again`() {
        val ctx = context().setValue("echo", "{rank}")

        assertEquals("{rank}", ctx.format("[echo]"))
    }

    @Test
    fun `reading a whole token returns the value itself`() {
        val ctx = context()

        assertEquals(3.14159, ctx.read("[hp]"))
        assertEquals("vip", ctx.read("{rank}"))
        assertEquals("19.9", ctx.read("%server_tps%"))
        assertEquals("from-action", ctx.read("same"))
    }

    private object Bridge : PlaceholderAdapter {
        override val id = "placeholderapi"
        override val state = PlaceholderAdapterState.AVAILABLE
        override val capabilities = PlaceholderAdapterCapabilities()
        override fun resolve(playerId: UUID?, expression: String): Any? =
            if (expression == "server_tps") "19.9" else null

        override fun publish(
            owner: PluginId,
            key: String,
            resolver: PlaceholderResolver<Any>,
            publication: PlaceholderPublication,
        ): ExternalPlaceholderRegistration = error("not used")
    }

    private object TestPlatform : PlatformAdapter {
        override val type = PlatformType.BUKKIT
        override val dataFolder = Files.createTempDirectory("pnlibrary-kinds-test")
        override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) = task.run()
        override fun executeReply(recipient: Any, task: Runnable) = task.run()
        override fun close() = Unit
    }
}
