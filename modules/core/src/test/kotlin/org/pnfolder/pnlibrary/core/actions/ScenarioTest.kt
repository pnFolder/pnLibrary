package org.pnfolder.pnlibrary.core.actions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.currency.ChargeAction
import org.pnfolder.pnlibrary.api.currency.Currency
import org.pnfolder.pnlibrary.api.currency.CurrencyRejectReason
import org.pnfolder.pnlibrary.api.currency.CurrencyResult
import org.pnfolder.pnlibrary.api.currency.CurrencyService
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.player.LibraryPlayer
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.AttemptAction
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.IfAction
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.RequireAction
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.StopAction
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.AllRequirement
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.AnyRequirement
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.CompareOperator
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.CompareRequirement
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.NotRequirement
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.PermissionRequirement
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.text.ComponentService
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** Scenarios: order, quiet stops, failures with undo, money and requirements. */
class ScenarioTest {
    private val id = UUID.randomUUID()
    private val player = mock(LibraryPlayer::class.java).also {
        `when`(it.uniqueId).thenReturn(id)
        `when`(it.name).thenReturn("Alice")
    }
    private val money = mock(Currency::class.java).also {
        `when`(it.format(BigDecimal(100))).thenReturn("100 coins")
    }
    private val log = mutableListOf<String>()

    private fun context(permissions: Set<String> = emptySet()): ActionContext {
        `when`(player.hasPermission(anyString())).thenAnswer { it.getArgument<String>(0) in permissions }
        val currencies = mock(CurrencyService::class.java)
        `when`(currencies.get("money")).thenReturn(money)
        return ActionContext(
            player = player,
            allPlayers = player,
            components = mock(ComponentService::class.java),
            logger = mock(PnLogger::class.java),
            tasks = mock(TaskScope::class.java),
            services = mapOf(CurrencyService::class.java to currencies),
        )
    }

    private fun step(name: String, result: ActionResult = ActionResult.Done) = Action { log += name; result }

    private fun pays() {
        `when`(money.withdraw(id, BigDecimal(100))).thenReturn(CompletableFuture.completedFuture(CurrencyResult.success()))
        `when`(money.deposit(id, BigDecimal(100))).thenAnswer {
            log += "refund"
            CompletableFuture.completedFuture(CurrencyResult.success())
        }
    }

    @Test
    fun `steps run in order and a stop ends the list quietly`() {
        val result = context().execute(listOf(step("a"), StopAction("enough"), step("b")))

        assertEquals(ActionResult.Stopped("enough"), result)
        assertEquals(listOf("a"), log)
    }

    @Test
    fun `a failure undoes earlier steps in reverse order and ends the list`() {
        val registering = { name: String ->
            Action { c -> c.onUndo(name) { log += "undo $name" }; ActionResult.Done }
        }

        val result = context().execute(
            listOf(registering("one"), registering("two"), step("boom", ActionResult.Failed("x", "broken")), step("after")),
        )

        assertEquals(ActionResult.Failed("x", "broken"), result)
        assertEquals(listOf("boom", "undo two", "undo one"), log)
    }

    @Test
    fun `a thrown exception becomes a failure`() {
        val result = context().execute(listOf(Action { error("oops") }))

        assertTrue(result is ActionResult.Failed && result.code == "action.error" && result.message == "oops")
    }

    @Test
    fun `an unmet requirement runs deny with the reason and stops`() {
        val deny = Action { c -> log += c.format("no: {reason}"); ActionResult.Done }

        val result = context().execute(listOf(RequireAction(PermissionRequirement("shop.use"), listOf(deny)), step("after")))

        assertTrue(result is ActionResult.Stopped)
        assertEquals(listOf("no: missing permission shop.use"), log)
    }

    @Test
    fun `if chooses a branch and the list goes on`() {
        val action = IfAction(PermissionRequirement("vip"), listOf(step("vip")), listOf(step("normal")))

        context(setOf("vip")).execute(listOf(action, step("end")))
        context().execute(listOf(action, step("end")))

        assertEquals(listOf("vip", "end", "normal", "end"), log)
    }

    @Test
    fun `a charge that cannot be paid fails with a code and attempt shows the error`() {
        `when`(money.withdraw(id, BigDecimal(100))).thenReturn(
            CompletableFuture.completedFuture(CurrencyResult.rejected(CurrencyRejectReason.INSUFFICIENT_FUNDS)),
        )
        val attempt = AttemptAction(
            steps = listOf(ChargeAction("money", "100"), step("give")),
            fail = listOf(Action { c -> log += c.format("{error_code}"); ActionResult.Done }),
        )

        val result = context().execute(listOf(attempt, step("after")))

        assertTrue(result is ActionResult.Stopped)
        assertEquals(listOf("currency.insufficient-funds"), log)
    }

    @Test
    fun `a charge is given back when a later step fails`() {
        pays()
        val attempt = AttemptAction(
            steps = listOf(ChargeAction("money", "100"), step("give", ActionResult.Failed("give", "no room"))),
            fail = listOf(step("shown")),
        )

        context().execute(listOf(attempt))

        assertEquals(listOf("give", "refund", "shown"), log)
    }

    @Test
    fun `a charge without attempt is given back and the failure reaches the caller`() {
        pays()

        val result = context().execute(listOf(ChargeAction("money", "100"), step("give", ActionResult.Failed("give", "no room"))))

        assertTrue(result is ActionResult.Failed)
        assertEquals(listOf("give", "refund"), log)
    }

    @Test
    fun `requirements compare values from any source and combine`() {
        val ctx = context(setOf("a")).setValue("balance", "250")
        val enough = CompareRequirement("{balance}", CompareOperator.GREATER_OR_EQUAL, "100")
        val rich = CompareRequirement("{balance}", CompareOperator.GREATER, "1000")

        assertTrue(enough.test(ctx).isMet)
        assertEquals(Verdict.Unmet("250 is not greater 1000"), rich.test(ctx))
        assertTrue(AllRequirement(listOf(enough, PermissionRequirement("a"))).test(ctx).isMet)
        assertTrue(AnyRequirement(listOf(rich, enough)).test(ctx).isMet)
        assertTrue(NotRequirement(rich).test(ctx).isMet)
        assertEquals("'x' is not a number", (CompareRequirement("x", CompareOperator.LESS, "1").test(ctx) as Verdict.Unmet).reason)
        assertTrue(Requirement { Verdict.Met }.test(ctx).isMet)
    }

    @Test
    fun `undo false keeps what the block did`() {
        pays()
        val attempt = AttemptAction(
            steps = listOf(ChargeAction("money", "100"), step("give", ActionResult.Failed("give", "no room"))),
            fail = listOf(step("shown")),
            undo = false,
        )

        context().execute(listOf(attempt))

        assertEquals(listOf("give", "shown"), log)
    }
}
