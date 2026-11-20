package org.pnfolder.pnlibrary.core.actions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.audiences.player.LibraryPlayer
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult
import org.pnfolder.pnlibrary.api.scenario.actions.DefinitionConflict
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.AttemptAction
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.CallAction
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.ErrorHandler
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.RandomAction
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.RandomOption
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.SetAction
import org.pnfolder.pnlibrary.api.scenario.expression.Expression
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionException
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.ExpressionRequirement
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.RefRequirement
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.text.ComponentService
import org.pnfolder.pnlibrary.core.plugin.module.LayeredLookup
import org.pnfolder.pnlibrary.core.plugin.module.ScenarioDefinitions
import java.util.UUID

/** The calculator, named definitions, calls and typed error handlers. */
class ExpressionTest {
    private val player = mock(LibraryPlayer::class.java).also {
        `when`(it.uniqueId).thenReturn(UUID.randomUUID())
        `when`(it.name).thenReturn("Alice")
        `when`(it.experience(true)).thenReturn(12.0)
        `when`(it.hasPermission(anyString())).thenAnswer { call -> call.getArgument<String>(0) == "shop.vip" }
    }
    private val shared = ScenarioDefinitions()
    private val own = ScenarioDefinitions()
    private val log = mutableListOf<String>()

    private fun context(vararg values: Pair<String, Any?>) = ActionContext(
        player = player,
        allPlayers = player,
        components = mock(ComponentService::class.java),
        logger = mock(PnLogger::class.java),
        tasks = mock(TaskScope::class.java),
        values = mapOf(*values),
        lookup = LayeredLookup(own, shared),
    )

    private fun calc(text: String, ctx: ActionContext = context()) = Expression.parse(text).text(ctx)

    @Test
    fun `arithmetic follows the usual precedence`() {
        assertEquals("14", calc("2 + 3 * 4"))
        assertEquals("20", calc("(2 + 3) * 4"))
        assertEquals("512", calc("2 ^ 3 ^ 2"))
        assertEquals("-1", calc("1 - 2"))
        assertEquals("2.5", calc("5 / 2"))
        assertEquals("1", calc("10 % 3"))
        assertEquals("ab3", calc("'ab' + 3"))
    }

    @Test
    fun `logic, comparison and the condition operator`() {
        assertEquals("true", calc("1 < 2 && 2 <= 2 && 3 != 4 && !(1 > 2)"))
        assertEquals("yes", calc("5 >= 10 ? 'big' : 5 >= 3 ? 'yes' : 'no'"))
        assertEquals("true", calc("false || true && true"))
        assertEquals("true", calc("'Abc' == 'Abc'"))
    }

    @Test
    fun `values come from the run, and text that looks like a number is a number`() {
        val ctx = context("balance" to "250", "rank" to "vip", "weird-name" to 7)

        assertEquals("25", calc("balance / 10", ctx))
        assertEquals("true", calc("rank == 'vip'", ctx))
        assertEquals("14", calc("{weird-name} * 2", ctx))
        assertEquals("false", calc("unknown_name > 1", ctx))
    }

    @Test
    fun `functions about the player and about numbers`() {
        assertEquals("true", calc("level() >= 10 && permission('shop.vip') && !permission('admin')"))
        assertEquals("3", calc("min(5, 3, 9)"))
        assertEquals("10", calc("clamp(50, 1, 10)"))
        assertEquals("3.14", calc("round(3.14159, 2)"))
        assertEquals("4", calc("sqrt(16)"))
        assertEquals("3", calc("len('abc')"))
        assertEquals("true", calc("contains(upper('admin team'), 'ADMIN')"))
        assertEquals("yes", calc("if(level() > 5, 'yes', 'no')"))
    }

    @Test
    fun `a plugin function joins the language`() {
        own.defineFunction("kills", { _, _ -> 120.0 }, "shop", DefinitionConflict.FAIL)

        assertEquals("true", calc("kills() >= 100"))
    }

    @Test
    fun `mistakes are reported with a position, and a bad calculation is simply not met`() {
        assertThrows(ExpressionException::class.java) { Expression.parse("1 +") }
        assertThrows(ExpressionException::class.java) { Expression.parse("(1 + 2") }
        assertThrows(ExpressionException::class.java) { ExpressionRequirement("2 $ 3") }
        val verdict = ExpressionRequirement("'a' * 2").test(context())
        assertTrue(verdict is Verdict.Unmet)
        assertFalse(ExpressionRequirement("1 / 0 > 1").test(context()).isMet)
    }

    @Test
    fun `a calculation inside a text and the set action`() {
        val ctx = context("base" to 200)
        SetAction("price", expression = "base * 0.9").run(ctx)
        SetAction("label", value = "{player} pays {price}").run(ctx)

        assertEquals("180", ctx.format("{= price}"))
        assertEquals("Alice pays 180", ctx.value("label"))
        assertEquals("level 24", ctx.format("level {= level() * 2}"))
        assertEquals("{= 1 +}", ctx.format("{= 1 +}"))
    }

    @Test
    fun `a defined action is called with arguments that do not leak`() {
        own.defineAction("greet", Action { c -> log += c.format("hello {who}"); ActionResult.Done }, "shop", DefinitionConflict.FAIL)
        val ctx = context()

        val result = ctx.execute(listOf(CallAction("greet", mapOf("who" to "{= 1 + 1}")), CallAction("greet", mapOf("who" to "Bob"))))

        assertEquals(ActionResult.Done, result)
        assertEquals(listOf("hello 2", "hello Bob"), log)
        assertEquals(null, ctx.value("who"))
        assertTrue(ctx.execute(listOf(CallAction("nope"))) is ActionResult.Failed)
    }

    @Test
    fun `a module name wins over a library one and conflicts follow the policy`() {
        shared.defineRequirement("vip", Requirement { Verdict.Unmet("library") }, "lib", DefinitionConflict.FAIL)
        own.defineRequirement("vip", Requirement { Verdict.Met }, "shop", DefinitionConflict.FAIL)

        assertTrue(RefRequirement("vip").test(context()).isMet)
        assertThrows(IllegalArgumentException::class.java) {
            own.defineRequirement("vip", Requirement { Verdict.Met }, "shop", DefinitionConflict.FAIL)
        }
        own.defineRequirement("vip", Requirement { Verdict.Unmet("kept?") }, "shop", DefinitionConflict.KEEP)
        assertTrue(RefRequirement("vip").test(context()).isMet)
        own.defineRequirement("vip", Requirement { Verdict.Unmet("replaced") }, "shop", DefinitionConflict.REPLACE)
        assertEquals(Verdict.Unmet("replaced"), RefRequirement("vip").test(context()))
        assertThrows(IllegalArgumentException::class.java) {
            own.defineRequirement("vip", Requirement { Verdict.Met }, "other-plugin", DefinitionConflict.REPLACE)
        }
        assertEquals(Verdict.Unmet("requirement 'zzz' is not defined"), RefRequirement("zzz").test(context()))
    }

    @Test
    fun `an error handler is chosen by the failure code`() {
        val failing = Action { ActionResult.Failed("currency.insufficient-funds", "poor") }
        fun say(text: String) = Action { c -> log += c.format(text); ActionResult.Done }
        val attempt = AttemptAction(
            steps = listOf(failing),
            handlers = listOf(
                ErrorHandler(listOf("shop.closed"), listOf(say("closed"))),
                ErrorHandler(listOf("currency.*"), listOf(say("money: {error_code}"))),
            ),
            fail = listOf(say("other")),
        )

        context().execute(listOf(attempt))
        context().execute(listOf(AttemptAction(listOf(Action { ActionResult.Failed("x.y", "boom") }), attempt.handlers, attempt.fail)))

        assertEquals(listOf("money: currency.insufficient-funds", "other"), log)
    }

    @Test
    fun `random picks by weight`() {
        val ctx = context()
        val rare = RandomOption(0.0001, listOf(Action { log += "rare"; ActionResult.Done }))
        val common = RandomOption(1e9, listOf(Action { log += "common"; ActionResult.Done }))
        val only = RandomAction(listOf(rare, common))

        repeat(20) { ctx.execute(listOf(only)) }

        assertTrue(log.count { it == "common" } >= 19)
    }
}
