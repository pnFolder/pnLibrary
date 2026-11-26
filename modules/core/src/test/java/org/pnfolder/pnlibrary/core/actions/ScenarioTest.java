package org.pnfolder.pnlibrary.core.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.api.currency.ChargeAction;
import org.pnfolder.pnlibrary.api.currency.Currency;
import org.pnfolder.pnlibrary.api.currency.CurrencyRejectReason;
import org.pnfolder.pnlibrary.api.currency.CurrencyResult;
import org.pnfolder.pnlibrary.api.currency.CurrencyService;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;
import org.pnfolder.pnlibrary.api.scenario.actions.ScenarioLookup;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.AttemptAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.IfAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.RequireAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.StopAction;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.AllRequirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.AnyRequirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.CompareOperator;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.CompareRequirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.NotRequirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.PermissionRequirement;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.text.ComponentService;
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType;

/** Scenarios: order, quiet stops, failures with undo, money and requirements. */
class ScenarioTest {
    private final UUID id = UUID.randomUUID();
    private Set<String> permissions = new HashSet<>();
    private final OnlinePlayer player = PlayerMocks.mockPlayer(id, name -> permissions.contains(name));
    private final Currency money = mock(Currency.class);
    private final List<String> log = new ArrayList<>();

    ScenarioTest() {
        when(money.format(new BigDecimal(100))).thenReturn("100 coins");
    }

    private ActionContext context() {
        return context(Collections.<String>emptySet());
    }

    private ActionContext context(Set<String> granted) {
        this.permissions = granted;
        CurrencyService currencies = mock(CurrencyService.class);
        when(currencies.get("money")).thenReturn(money);
        Map<Class<?>, Object> services = new HashMap<>();
        services.put(CurrencyService.class, currencies);
        return new ActionContext(player, player, mock(ComponentService.class), mock(PnLogger.class), mock(TaskScope.class),
            ComponentSerializerType.ADAPTIVE, Collections.<String, Object>emptyMap(), services, type -> null,
            ScenarioLookup.NONE, null);
    }

    private Action step(final String name) {
        return step(name, ActionResult.Done.INSTANCE);
    }

    private Action step(final String name, final ActionResult result) {
        return context -> {
            log.add(name);
            return result;
        };
    }

    private void pays() {
        when(money.withdraw(id, new BigDecimal(100))).thenReturn(CompletableFuture.completedFuture(CurrencyResult.success()));
        when(money.deposit(id, new BigDecimal(100))).thenAnswer(call -> {
            log.add("refund");
            return CompletableFuture.completedFuture(CurrencyResult.success());
        });
    }

    private static List<Action> list(Action... actions) {
        return Arrays.asList(actions);
    }

    @Test
    void stepsRunInOrderAndAStopEndsTheListQuietly() {
        ActionResult result = context().execute(list(step("a"), new StopAction("enough"), step("b")));

        assertEquals(new ActionResult.Stopped("enough"), result);
        assertEquals(Collections.singletonList("a"), log);
    }

    @Test
    void aFailureUndoesEarlierStepsInReverseOrderAndEndsTheList() {
        ActionResult result = context().execute(list(registering("one"), registering("two"),
            step("boom", new ActionResult.Failed("x", "broken")), step("after")));

        assertEquals(new ActionResult.Failed("x", "broken"), result);
        assertEquals(Arrays.asList("boom", "undo two", "undo one"), log);
    }

    private Action registering(final String name) {
        return context -> {
            context.onUndo(name, () -> log.add("undo " + name));
            return ActionResult.Done.INSTANCE;
        };
    }

    @Test
    void aThrownExceptionBecomesAFailure() {
        ActionResult result = context().execute(list(context -> {
            throw new IllegalStateException("oops");
        }));

        assertTrue(result instanceof ActionResult.Failed);
        assertEquals("action.error", ((ActionResult.Failed) result).getCode());
        assertEquals("oops", ((ActionResult.Failed) result).getMessage());
    }

    @Test
    void anUnmetRequirementRunsDenyWithTheReasonAndStops() {
        Action deny = context -> {
            log.add(context.format("no: {reason}"));
            return ActionResult.Done.INSTANCE;
        };

        ActionResult result = context().execute(list(new RequireAction(new PermissionRequirement("shop.use"), list(deny)), step("after")));

        assertTrue(result instanceof ActionResult.Stopped);
        assertEquals(Collections.singletonList("no: missing permission shop.use"), log);
    }

    @Test
    void ifChoosesABranchAndTheListGoesOn() {
        Action action = new IfAction(new PermissionRequirement("vip"), list(step("vip")), list(step("normal")));

        context(Collections.singleton("vip")).execute(list(action, step("end")));
        context().execute(list(action, step("end")));

        assertEquals(Arrays.asList("vip", "end", "normal", "end"), log);
    }

    @Test
    void aChargeThatCannotBePaidFailsWithACodeAndAttemptShowsTheError() {
        when(money.withdraw(id, new BigDecimal(100))).thenReturn(
            CompletableFuture.completedFuture(CurrencyResult.rejected(CurrencyRejectReason.INSUFFICIENT_FUNDS)));
        AttemptAction attempt = new AttemptAction(list(new ChargeAction("money", "100"), step("give")), Collections.emptyList(),
            list(context -> {
                log.add(context.format("{error_code}"));
                return ActionResult.Done.INSTANCE;
            }));

        ActionResult result = context().execute(list(attempt, step("after")));

        assertTrue(result instanceof ActionResult.Stopped);
        assertEquals(Collections.singletonList("currency.insufficient-funds"), log);
    }

    @Test
    void aChargeIsGivenBackWhenALaterStepFails() {
        pays();
        AttemptAction attempt = new AttemptAction(list(new ChargeAction("money", "100"),
            step("give", new ActionResult.Failed("give", "no room"))), Collections.emptyList(), list(step("shown")));

        context().execute(list(attempt));

        assertEquals(Arrays.asList("give", "refund", "shown"), log);
    }

    @Test
    void aChargeWithoutAttemptIsGivenBackAndTheFailureReachesTheCaller() {
        pays();

        ActionResult result = context().execute(list(new ChargeAction("money", "100"),
            step("give", new ActionResult.Failed("give", "no room"))));

        assertTrue(result instanceof ActionResult.Failed);
        assertEquals(Arrays.asList("give", "refund"), log);
    }

    @Test
    void requirementsCompareValuesFromAnySourceAndCombine() {
        ActionContext ctx = context(Collections.singleton("a")).setValue("balance", "250");
        CompareRequirement enough = new CompareRequirement("{balance}", CompareOperator.GREATER_OR_EQUAL, "100");
        CompareRequirement rich = new CompareRequirement("{balance}", CompareOperator.GREATER, "1000");

        assertTrue(enough.test(ctx).isMet());
        assertEquals(new Verdict.Unmet("250 is not greater 1000"), rich.test(ctx));
        assertTrue(new AllRequirement(Arrays.<Requirement>asList(enough, new PermissionRequirement("a"))).test(ctx).isMet());
        assertTrue(new AnyRequirement(Arrays.<Requirement>asList(rich, enough)).test(ctx).isMet());
        assertTrue(new NotRequirement(rich).test(ctx).isMet());
        assertEquals("'x' is not a number",
            ((Verdict.Unmet) new CompareRequirement("x", CompareOperator.LESS, "1").test(ctx)).getReason());
        Requirement always = context -> Verdict.Met.INSTANCE;
        assertTrue(always.test(ctx).isMet());
    }

    @Test
    void undoFalseKeepsWhatTheBlockDid() {
        pays();
        AttemptAction attempt = new AttemptAction(list(new ChargeAction("money", "100"),
            step("give", new ActionResult.Failed("give", "no room"))), Collections.emptyList(), list(step("shown")), false, false);

        context().execute(list(attempt));

        assertEquals(Arrays.asList("give", "shown"), log);
    }
}
