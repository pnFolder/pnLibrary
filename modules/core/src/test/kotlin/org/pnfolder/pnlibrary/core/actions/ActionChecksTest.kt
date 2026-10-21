package org.pnfolder.pnlibrary.core.actions

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.actions.CommandAction
import org.pnfolder.pnlibrary.api.actions.CommandRunner
import org.pnfolder.pnlibrary.api.actions.Comparison
import org.pnfolder.pnlibrary.api.actions.ConditionSerializer
import org.pnfolder.pnlibrary.api.actions.ConditionalAction
import org.pnfolder.pnlibrary.api.actions.ExperienceCondition
import org.pnfolder.pnlibrary.api.actions.ItemCondition
import org.pnfolder.pnlibrary.api.actions.ItemQuery
import org.pnfolder.pnlibrary.api.actions.LengthCondition
import org.pnfolder.pnlibrary.api.actions.LibraryAudience
import org.pnfolder.pnlibrary.api.actions.LibraryPlayer
import org.pnfolder.pnlibrary.api.actions.NearCondition
import org.pnfolder.pnlibrary.api.actions.PermissionsCondition
import org.pnfolder.pnlibrary.api.actions.PlayerEffect
import org.pnfolder.pnlibrary.api.actions.PlayerParticle
import org.pnfolder.pnlibrary.api.actions.RegexCondition
import org.pnfolder.pnlibrary.api.actions.Requirement
import org.pnfolder.pnlibrary.api.actions.RequirementsAction
import org.pnfolder.pnlibrary.api.actions.StopAction
import org.pnfolder.pnlibrary.api.actions.TypeCondition
import org.pnfolder.pnlibrary.api.actions.ValueCondition
import org.pnfolder.pnlibrary.api.actions.ValueType
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.text.ComponentService
import java.util.UUID

class ActionChecksTest {
    private val log = mutableListOf<String>()

    private fun context(player: FakePlayer = FakePlayer()): ActionContext = ActionContext(
        player = player,
        allPlayers = mock(LibraryAudience::class.java),
        components = mock(ComponentService::class.java),
        logger = mock(PnLogger::class.java),
        tasks = mock(TaskScope::class.java),
    )

    private val recording = Action { log += "ran" }

    private fun say(text: String) = Action { log += text }

    private fun atLeast(key: String, value: String) = ValueCondition(key, Comparison.GREATER_OR_EQUAL, value)

    // ---- stop and command ----

    @Test
    fun `stop in a branch ends the whole invocation`() {
        val ctx = context().setValue("slots", 0)

        ctx.execute(listOf(ConditionalAction(
            allChecks = listOf(atLeast("slots", "1")),
            thenActions = listOf(recording),
            elseActions = listOf(StopAction()),
        ), recording))

        assertEquals(emptyList<String>(), log)
        assertTrue(ctx.stopped)
    }

    @Test
    fun `without stop the rest of the list runs`() {
        val ctx = context().setValue("slots", 3)

        ctx.execute(listOf(ConditionalAction(
            allChecks = listOf(atLeast("slots", "1")),
            thenActions = listOf(recording),
            elseActions = listOf(StopAction()),
        ), recording))

        assertEquals(listOf("ran", "ran"), log)
        assertFalse(ctx.stopped)
    }

    @Test
    fun `command replaces the player name and strips the leading slash`() {
        val player = FakePlayer()
        val ctx = context(player)

        CommandAction("/give [[player]] apple 1", CommandRunner.CONSOLE).execute(ctx)
        CommandAction("spawn", CommandRunner.PLAYER).execute(ctx)

        assertEquals(listOf("console: give Alice apple 1", "player: spawn"), player.commands)
    }

    @Test
    fun `unknown placeholders stay visible in the command text`() {
        val player = FakePlayer()

        CommandAction("say [[missing]]").execute(context(player))

        assertEquals(listOf("console: say [[missing]]"), player.commands)
    }

    // ---- requirements ----

    private fun shop(
        minimum: Int = 0,
        stopAtSuccess: Boolean = false,
    ) = RequirementsAction(
        requirements = linkedMapOf(
            "space" to Requirement(atLeast("slots", "1"), deny = listOf(say("no space")), success = listOf(say("space ok"))),
            "money" to Requirement(atLeast("money", "100"), deny = listOf(say("no money"))),
            "vip" to Requirement(atLeast("vip", "1"), optional = true, success = listOf(say("vip ok"))),
            "bonus" to Requirement(atLeast("bonus", "1"), optional = true, success = listOf(say("bonus ok"))),
        ),
        minimum = minimum,
        stopAtSuccess = stopAtSuccess,
        success = listOf(say("given")),
        deny = listOf(say("denied")),
    )

    @Test
    fun `requirements run the success actions of matched checks and then the shared success`() {
        val ctx = context().setValue("slots", 2).setValue("money", 150)

        ctx.execute(listOf(shop(), recording))

        assertEquals(listOf("space ok", "given", "ran"), log)
    }

    @Test
    fun `the first failed requirement runs its own deny then the shared one and stops the chain`() {
        val ctx = context().setValue("slots", 0).setValue("money", 0)

        ctx.execute(listOf(shop(), recording))

        assertEquals(listOf("no space", "denied"), log)
        assertTrue(ctx.stopped)
    }

    @Test
    fun `optional requirements may fail unless a minimum is set`() {
        val ctx = context().setValue("slots", 1).setValue("money", 100)

        shop().execute(ctx)

        assertEquals(listOf("space ok", "given"), log)
    }

    @Test
    fun `minimum counts only optional requirements and still needs all required ones`() {
        val both = context().setValue("slots", 1).setValue("money", 100).setValue("vip", 1).setValue("bonus", 1)
        val one = context().setValue("slots", 1).setValue("money", 100).setValue("vip", 1)
        val none = context().setValue("slots", 1).setValue("money", 100)

        shop(minimum = 2).execute(both)
        assertEquals(listOf("space ok", "vip ok", "bonus ok", "given"), log)

        log.clear()
        shop(minimum = 2).execute(one)
        assertEquals(listOf("denied"), log)

        log.clear()
        shop(minimum = 1).execute(none)
        assertEquals(listOf("denied"), log)
    }

    @Test
    fun `stop-at-success skips optional checks once the minimum is reached`() {
        val ctx = context().setValue("slots", 1).setValue("money", 100).setValue("vip", 1).setValue("bonus", 1)

        shop(minimum = 1, stopAtSuccess = true).execute(ctx)

        assertEquals(listOf("space ok", "vip ok", "given"), log)
    }

    @Test
    fun `a bang before the type inverts a check`() {
        val action = RequirementsAction(
            requirements = linkedMapOf("not banned" to Requirement(ConditionSerializer.parse(
                mapOf("type" to "!>=", "input" to "banned", "output" to "1"),
            ))),
            success = listOf(say("welcome")),
        )

        action.execute(context().setValue("banned", 0))
        assertEquals(listOf("welcome"), log)

        log.clear()
        action.execute(context().setValue("banned", 1))
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `a requirement without a check counts as failed`() {
        val action = RequirementsAction(
            requirements = linkedMapOf("empty" to Requirement(null, deny = listOf(say("bad")))),
            success = listOf(say("given")),
        )

        action.execute(context())

        assertEquals(listOf("bad"), log)
    }

    @Test
    fun `minimum above the number of optional checks is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            RequirementsAction(requirements = mapOf("a" to Requirement(atLeast("a", "1"))), minimum = 1)
        }
    }

    // ---- conditions ----

    @Test
    fun `permissions need all entries or the minimum`() {
        val ctx = context(FakePlayer(permissions = setOf("a.use", "b.use")))

        assertTrue(PermissionsCondition(listOf("a.use", "b.use")).matches(ctx))
        assertFalse(PermissionsCondition(listOf("a.use", "c.use")).matches(ctx))
        assertTrue(PermissionsCondition(listOf("a.use", "c.use"), minimum = 1).matches(ctx))
        assertFalse(PermissionsCondition(emptyList()).matches(ctx))
    }

    @Test
    fun `has-item passes the description to the player and compares the count`() {
        val player = FakePlayer(items = mapOf("diamond" to 5))
        val ctx = context(player)

        assertTrue(ItemCondition("diamond", 5, nameContains = "Key").matches(ctx))
        assertFalse(ItemCondition("diamond", 6).matches(ctx))
        assertFalse(ItemCondition("", 1).matches(ctx))
        assertEquals(ItemQuery("diamond", null, "Key", null, true), player.queries.first())
    }

    @Test
    fun `has-item never matches on a platform without inventories`() {
        assertFalse(ItemCondition("diamond").matches(context(FakePlayer(inventory = false))))
    }

    @Test
    fun `has-exp compares points or levels`() {
        val ctx = context(FakePlayer(points = 500.0, levels = 12.0))

        assertTrue(ExperienceCondition(500).matches(ctx))
        assertFalse(ExperienceCondition(501).matches(ctx))
        assertTrue(ExperienceCondition(12, level = true).matches(ctx))
        assertFalse(ExperienceCondition(13, level = true).matches(ctx))
    }

    @Test
    fun `is-near compares the distance in the named world`() {
        val ctx = context(FakePlayer(distance = 4.0))

        assertTrue(NearCondition("world,10,64,10", 5.0).matches(ctx))
        assertFalse(NearCondition("world,10,64,10", 3.0).matches(ctx))
        assertFalse(NearCondition("nether,10,64,10", 5.0).matches(ctx))
        assertThrows(IllegalArgumentException::class.java) { NearCondition("world,10,64", 5.0) }
    }

    @Test
    fun `regex must match the whole value`() {
        val ctx = context().setValue("code", "AB-123")

        assertTrue(RegexCondition("code", "[A-Z]{2}-\\d+").matches(ctx))
        assertFalse(RegexCondition("code", "\\d+").matches(ctx))
        assertTrue(RegexCondition("id-[[code]]", "id-AB-123").matches(ctx))
        assertThrows(IllegalArgumentException::class.java) { RegexCondition("code", "[") }
    }

    @Test
    fun `length checks the range`() {
        val ctx = context().setValue("word", "hello")

        assertTrue(LengthCondition("word", 3, 5).matches(ctx))
        assertFalse(LengthCondition("word", 6, 10).matches(ctx))
        assertThrows(IllegalArgumentException::class.java) { LengthCondition("word", 5, 3) }
    }

    @Test
    fun `is-type recognizes numbers and uuids`() {
        val ctx = context().setValue("n", "42").setValue("d", "4.5").setValue("u", UUID.randomUUID().toString())

        assertTrue(TypeCondition("n", ValueType.INT).matches(ctx))
        assertFalse(TypeCondition("d", ValueType.INT).matches(ctx))
        assertTrue(TypeCondition("d", ValueType.DOUBLE).matches(ctx))
        assertTrue(TypeCondition("u", ValueType.UUID).matches(ctx))
        assertFalse(TypeCondition("n", ValueType.UUID).matches(ctx))
    }

    private class FakePlayer(
        private val permissions: Set<String> = emptySet(),
        private val items: Map<String, Int> = emptyMap(),
        private val inventory: Boolean = true,
        private val points: Double = 0.0,
        private val levels: Double = 0.0,
        private val distance: Double = 0.0,
    ) : LibraryPlayer {
        val commands = mutableListOf<String>()
        val queries = mutableListOf<ItemQuery>()
        override val uniqueId: UUID = UUID.randomUUID()
        override val name = "Alice"
        override fun hasPermission(permission: String) = permission in permissions
        override fun sendMessage(text: Component) = Unit
        override fun actionBar(text: Component) = Unit
        override fun playSound(sound: Sound) = true
        override fun applyEffect(effect: PlayerEffect) = false
        override fun spawnParticle(particle: PlayerParticle) = false
        override fun runCommand(command: String, asConsole: Boolean): Boolean {
            commands += (if (asConsole) "console: " else "player: ") + command
            return true
        }
        override fun countItems(query: ItemQuery): Int? {
            queries += query
            return if (inventory) items[query.material] ?: 0 else null
        }
        override fun experience(asLevels: Boolean): Double = if (asLevels) levels else points
        override fun distanceTo(world: String, x: Double, y: Double, z: Double): Double? =
            if (world == "world") distance else null
    }
}
