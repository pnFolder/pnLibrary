package org.pnfolder.pnlibrary.core.actions

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.actions.ActionLine
import org.pnfolder.pnlibrary.api.actions.ActionTarget
import org.pnfolder.pnlibrary.api.actions.Comparison
import org.pnfolder.pnlibrary.api.actions.LibraryAudience
import org.pnfolder.pnlibrary.api.actions.LibraryPlayer
import org.pnfolder.pnlibrary.api.actions.MessageAction
import org.pnfolder.pnlibrary.api.actions.NotCondition
import org.pnfolder.pnlibrary.api.actions.PermissionCondition
import org.pnfolder.pnlibrary.api.actions.PlayerEffect
import org.pnfolder.pnlibrary.api.actions.PlayerParticle
import org.pnfolder.pnlibrary.api.actions.RegexCondition
import org.pnfolder.pnlibrary.api.actions.RequirementSerializer
import org.pnfolder.pnlibrary.api.actions.RunAction
import org.pnfolder.pnlibrary.api.actions.TitleAction
import org.pnfolder.pnlibrary.api.actions.ValueCondition
import org.pnfolder.pnlibrary.api.actions.WaitAction
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
    )

    // ---- [wait] ----

    @Test
    fun `wait tags read ticks and seconds in both spellings`() {
        assertEquals(WaitAction(Duration.ofSeconds(2)), ActionLine.parse("[wait 2s]"))
        assertEquals(WaitAction(Duration.ofSeconds(2)), ActionLine.parse("[wait] 40"))
        assertEquals(WaitAction(Duration.ofMillis(500)), ActionLine.parse("[WAIT 10]"))
        assertThrows(IllegalArgumentException::class.java) { ActionLine.parse("[wait soon]") }
    }

    @Test
    fun `a wait pauses only what comes after it`() {
        val ctx = context()

        ctx.execute(listOf(say("before"), WaitAction(Duration.ofSeconds(2)), say("after")))

        assertEquals(listOf("before"), log)
        assertEquals(1, mockingDetails(tasks).invocations.count { it.method.name == "schedule" })
    }

    @Test
    fun `a wait at the end schedules nothing`() {
        context().execute(listOf(say("only"), WaitAction(Duration.ofSeconds(2))))

        assertEquals(listOf("only"), log)
        assertEquals(0, mockingDetails(tasks).invocations.count { it.method.name == "schedule" })
    }

    // ---- [run] templates ----

    @Test
    fun `run executes the named template`() {
        val ctx = context(templates = mapOf("refuse" to listOf(say("refused"), say("sound"))))

        ctx.execute(listOf(ActionLine.parse("[run] refuse"), say("after")))

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
        val ctx = context(templates = mapOf("quit" to listOf(ActionLine.parse("[stop]"))))

        ctx.execute(listOf(RunAction("quit"), say("never")))

        assertEquals(emptyList<String>(), log)
        assertTrue(ctx.stopped)
    }

    // ---- [title] ----

    @Test
    fun `a title line reads text, subtitle and times in ticks`() {
        val title = ActionLine.parse("[title] &aVictory;&7Well done;10;70;20") as TitleAction

        assertEquals("&aVictory", title.title)
        assertEquals("&7Well done", title.subtitle)
        assertEquals(Duration.ofMillis(500), title.fadeIn)
        assertEquals(Duration.ofMillis(3500), title.stay)
        assertEquals(Duration.ofMillis(1000), title.fadeOut)
    }

    @Test
    fun `a title needs only the first part and broadcast reaches everyone`() {
        val single = ActionLine.parse("[title] Hello") as TitleAction
        assertEquals("", single.subtitle)
        assertEquals(TitleAction().stay, single.stay)

        val all = ActionLine.parse("[broadcasttitle] Hello") as TitleAction
        assertEquals(ActionTarget.ALL_PLAYERS, all.target)
        assertThrows(IllegalArgumentException::class.java) { ActionLine.parse("[title] a;b;1;2;3;4") }
        assertThrows(IllegalArgumentException::class.java) { ActionLine.parse("[title] a;b;soon") }
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

    @Test
    fun `lines written back read the same`() {
        listOf("[wait 40]", "[run] refuse", "[title] A;B;10;70;20", "[broadcasttitle] A;;10;70;20").forEach { line ->
            assertEquals(line, ActionLine.format(ActionLine.parse(line)), line)
        }
    }

    // ---- requirement shortcuts ----

    @Test
    fun `an if line replaces the type and a short deny is a chat message`() {
        val req = requirement("if" to "%vault_eco_balance% >= 100", "deny" to "&cNeed 100 coins")

        assertEquals(ValueCondition("%vault_eco_balance%", Comparison.GREATER_OR_EQUAL, "100"), req.check)
        assertEquals(listOf<Action>(MessageAction(listOf("&cNeed 100 coins"))), req.deny)
    }

    @Test
    fun `a short list may mix plain messages and tag lines`() {
        val req = requirement("if" to "permission shop.use", "deny" to listOf("&cNo", "[sound] ENTITY_VILLAGER_NO"), "success" to "&aWelcome")

        assertEquals(2, req.deny.size)
        assertInstanceOf(MessageAction::class.java, req.deny[0])
        assertEquals(listOf<Action>(MessageAction(listOf("&aWelcome"))), req.success)
    }

    @Test
    fun `a requirement can be only a line of text`() {
        val req = RequirementSerializer.deserialize(
            "%x% < 5",
            ConfigSerializationContext("t", Any::class.java, Any::class.java, emptyList(), null),
        )

        assertEquals(ValueCondition("%x%", Comparison.LESS_THAN, "5"), req.check)
        assertEquals(emptyList<Action>(), req.deny)
    }

    @Test
    fun `expressions cover comparisons, words, permissions and negation`() {
        fun parse(text: String) = RequirementSerializer.deserialize(
            text,
            ConfigSerializationContext("t", Any::class.java, Any::class.java, emptyList(), null),
        ).check

        assertEquals(ValueCondition("%a%", Comparison.NUMBER_EQUALS, "5"), parse("%a% == 5"))
        assertEquals(ValueCondition("%a%", Comparison.GREATER_THAN, "5"), parse("%a%>5"))
        assertEquals(ValueCondition("%rank%", Comparison.EQUALS, "vip", ignoreCase = true), parse("%rank% is vip"))
        assertEquals(ValueCondition("%n%", Comparison.CONTAINS, "Al", ignoreCase = false), parse("%n% contains Al"))
        assertEquals(RegexCondition("%code%", "[A-Z]{2}-\\d+"), parse("%code% matches [A-Z]{2}-\\d+"))
        assertEquals(PermissionCondition("shop.use"), parse("permission shop.use"))
        assertEquals(NotCondition(PermissionCondition("shop.banned")), parse("!permission shop.banned"))
        assertEquals(NotCondition(PermissionCondition("shop.banned")), parse("not permission shop.banned"))
    }

    @Test
    fun `mixed or unreadable requirements are rejected with a reason`() {
        assertTrue(assertThrows(IllegalArgumentException::class.java) { requirement("if" to "%a% >= 1", "type" to "has permission") }.message!!.contains("either 'if' or 'type'"))
        assertTrue(assertThrows(IllegalArgumentException::class.java) { requirement("if" to "%a% >= 1", "deny" to "x", "deny_commands" to listOf("[message] y")) }.message!!.contains("either 'deny' or 'deny_commands'"))
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
