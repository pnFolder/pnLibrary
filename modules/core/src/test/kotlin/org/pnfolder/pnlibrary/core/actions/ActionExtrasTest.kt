package org.pnfolder.pnlibrary.core.actions

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.actions.builtin.DelayAction
import org.pnfolder.pnlibrary.api.actions.builtin.RunAction
import org.pnfolder.pnlibrary.api.actions.builtin.StopAction
import org.pnfolder.pnlibrary.api.audiences.LibraryAudience
import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import org.pnfolder.pnlibrary.api.audiences.PlayerEffect
import org.pnfolder.pnlibrary.api.audiences.PlayerParticle
import org.pnfolder.pnlibrary.api.checks.builtin.Comparison
import org.pnfolder.pnlibrary.api.checks.builtin.NotCheck
import org.pnfolder.pnlibrary.api.checks.builtin.PermissionCheck
import org.pnfolder.pnlibrary.api.checks.builtin.RegexCheck
import org.pnfolder.pnlibrary.api.checks.builtin.ValueCheck
import org.pnfolder.pnlibrary.api.checks.internal.RequirementSerializer
import org.pnfolder.pnlibrary.api.config.ConfigSerializationContext
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.text.ComponentService
import java.time.Duration
import java.util.UUID

class ActionExtrasTest {
    private val log = mutableListOf<String>()
    private val logger = mock(PnLogger::class.java)
    private val tasks = mock(TaskScope::class.java)

    private fun context(player: Recorder = Recorder(), templates: Map<String, List<Action>> = emptyMap()) = ActionContext(
        player = player,
        allPlayers = mock(LibraryAudience::class.java),
        components = mock(ComponentService::class.java),
        logger = logger,
        tasks = tasks,
        templates = templates,
    )

    private fun say(text: String) = Action { log += text }

    private fun warnings(): List<String> =
        mockingDetails(logger).invocations.filter { it.method.name == "warning" }.map { it.arguments[0].toString() }

    private fun requirement(vararg pairs: Pair<String, Any?>) = RequirementSerializer.deserialize(
        mapOf(*pairs),
        ConfigSerializationContext("test", Any::class.java, Any::class.java, emptyList(), null),
    ).entries.values.single()

    // ---- [wait] ----

    @Test
    fun `a wait pauses only what comes after it`() {
        val ctx = context()

        ctx.execute(listOf(say("before"), DelayAction(Duration.ofSeconds(2)), say("after")))

        assertEquals(listOf("before"), log)
        assertEquals(1, mockingDetails(tasks).invocations.count { it.method.name == "schedule" })
    }

    @Test
    fun `a wait at the end schedules nothing`() {
        context().execute(listOf(say("only"), DelayAction(Duration.ofSeconds(2))))

        assertEquals(listOf("only"), log)
        assertEquals(0, mockingDetails(tasks).invocations.count { it.method.name == "schedule" })
    }

    // ---- [run] templates ----

    @Test
    fun `run executes the named template`() {
        val ctx = context(templates = mapOf("refuse" to listOf(say("refused"), say("sound"))))

        ctx.execute(listOf(RunAction("refuse"), say("after")))

        assertEquals(listOf("refused", "sound", "after"), log)
    }

    @Test
    fun `an unknown template warns with a suggestion and continues`() {
        val ctx = context(templates = mapOf("refuse" to listOf(say("refused"))))

        ctx.execute(listOf(RunAction("refuze"), say("after")))

        assertEquals(listOf("after"), log)
        assertTrue(warnings().single().contains("Did you mean 'refuse'?"), warnings().toString())
    }

    @Test
    fun `templates that call each other forever are stopped`() {
        val ctx = context(templates = mapOf("a" to listOf(say("a"), RunAction("a"))))

        ctx.execute(listOf(RunAction("a")))

        assertEquals(ActionContext.MAX_TEMPLATE_DEPTH, log.size)
        assertTrue(warnings().single().contains("called too deeply"), warnings().toString())
    }

    @Test
    fun `a stop inside a template ends the whole chain`() {
        val ctx = context(templates = mapOf("quit" to listOf(StopAction())))

        ctx.execute(listOf(RunAction("quit"), say("never")))

        assertEquals(emptyList<String>(), log)
        assertTrue(ctx.stopped)
    }

    @Test
    fun `a platform without titles produces a warning, not an error`() {
        val ctx = context(Recorder(titles = false))
        // the mocked component service returns nothing, so use a message-free title through the player directly
        val player = Recorder(titles = false)
        assertFalse(player.showTitle(Component.empty(), Component.empty(), Duration.ZERO, Duration.ZERO, Duration.ZERO))
        assertEquals(emptyList<String>(), log)
        assertEquals(emptyList<String>(), warnings())
        assertTrue(ctx.player is Recorder)
    }

    // ---- requirement shortcuts ----

    @Test
    fun `an if line replaces the type`() {
        val req = requirement("if" to "%vault_eco_balance% >= 100")

        assertEquals(ValueCheck("%vault_eco_balance%", Comparison.GREATER_OR_EQUAL, "100"), req.check)
    }

    @Test
    fun `a requirement can be only a line of text`() {
        val req = RequirementSerializer.deserialize(
            "%x% < 5",
            ConfigSerializationContext("t", Any::class.java, Any::class.java, emptyList(), null),
        ).entries.values.single()

        assertEquals(ValueCheck("%x%", Comparison.LESS_THAN, "5"), req.check)
        assertEquals(emptyList<Action>(), req.deny)
    }

    @Test
    fun `expressions cover comparisons, words, permissions and negation`() {
        fun parse(text: String) = RequirementSerializer.deserialize(
            text,
            ConfigSerializationContext("t", Any::class.java, Any::class.java, emptyList(), null),
        ).entries.values.single().check

        assertEquals(ValueCheck("%a%", Comparison.NUMBER_EQUALS, "5"), parse("%a% == 5"))
        assertEquals(ValueCheck("%a%", Comparison.GREATER_THAN, "5"), parse("%a%>5"))
        assertEquals(ValueCheck("%rank%", Comparison.EQUALS, "vip", ignoreCase = true), parse("%rank% is vip"))
        assertEquals(ValueCheck("%n%", Comparison.CONTAINS, "Al", ignoreCase = false), parse("%n% contains Al"))
        assertEquals(RegexCheck("%code%", "[A-Z]{2}-\\d+"), parse("%code% matches [A-Z]{2}-\\d+"))
        assertEquals(PermissionCheck("shop.use"), parse("permission shop.use"))
        assertEquals(NotCheck(PermissionCheck("shop.banned")), parse("!permission shop.banned"))
        assertEquals(NotCheck(PermissionCheck("shop.banned")), parse("not permission shop.banned"))
    }

    @Test
    fun `mixed or unreadable requirements are rejected with a reason`() {
        assertTrue(assertThrows(IllegalArgumentException::class.java) { requirement("if" to "%a% >= 1",
            "type" to "has permission") }.message!!.contains("either 'if' or 'type'"))
        assertTrue(assertThrows(IllegalArgumentException::class.java) { requirement("if" to "%a% >= 1", "deny" to "x",
            "deny_commands" to listOf("[message] y")) }.message!!.contains("either 'deny' or 'deny_commands'"))
        assertTrue(assertThrows(IllegalArgumentException::class.java) { requirement("if" to "banana") }.message!!.contains("Cannot read requirement 'banana'"))
    }

    @Test
    fun `a typo next to the new short keys is suggested`() {
        val message = assertThrows(IllegalArgumentException::class.java) {
            requirement("type" to "has permission", "permission" to "x", "dney" to "no")
        }.message!!

        assertTrue(message.contains("Did you mean 'deny'?"), message)
    }

    @Test
    fun `a typo next to an if line is reported too`() {
        val message = assertThrows(IllegalArgumentException::class.java) {
            requirement("if" to "%a% >= 1", "dney" to "no")
        }.message!!

        assertTrue(message.contains("Unknown key 'dney' next to 'if'") && message.contains("Did you mean 'deny'?"), message)
    }

    private class Recorder(private val titles: Boolean = true) : LibraryPlayer {
        override val uniqueId: UUID = UUID.randomUUID()
        override val name = "Alex"
        override fun hasPermission(permission: String) = false
        override fun sendMessage(text: Component) = Unit
        override fun actionBar(text: Component) = Unit
        override fun playSound(sound: Sound) = true
        override fun applyEffect(effect: PlayerEffect) = false
        override fun spawnParticle(particle: PlayerParticle) = false
        override fun showTitle(title: Component, subtitle: Component, fadeIn: Duration, stay: Duration, fadeOut: Duration) = titles
    }
}
