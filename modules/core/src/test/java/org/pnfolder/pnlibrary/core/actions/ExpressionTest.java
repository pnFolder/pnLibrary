package org.pnfolder.pnlibrary.core.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.registry.Visibility;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;
import org.pnfolder.pnlibrary.api.scenario.actions.DefinitionConflict;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.AttemptAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.CallAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.ErrorHandler;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.RandomAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.RandomOption;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.SetAction;
import org.pnfolder.pnlibrary.api.scenario.expression.Expression;
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionException;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.ExpressionRequirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.RefRequirement;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.text.ComponentService;
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType;
import org.pnfolder.pnlibrary.core.plugin.module.definition.ScenarioDefinitions;

/** The calculator, named definitions, calls and typed error handlers. */
class ExpressionTest {
    private final OnlinePlayer player = PlayerMocks.mockPlayer(name -> name.equals("shop.vip"));
    private final ScenarioDefinitions definitions = new ScenarioDefinitions();
    private final List<String> log = new ArrayList<>();

    ExpressionTest() {
        when(player.experienceLevel()).thenReturn(12.0);
    }

    private ActionContext context(Object... namesAndValues) {
        Map<String, Object> values = new HashMap<>();
        for (int index = 0; index < namesAndValues.length; index += 2) {
            values.put((String) namesAndValues[index], namesAndValues[index + 1]);
        }
        return new ActionContext(player, player, mock(ComponentService.class), mock(PnLogger.class), mock(TaskScope.class),
            ComponentSerializerType.ADAPTIVE, values, Collections.emptyMap(), type -> null,
            definitions.view("shop/main"), null);
    }

    private String calc(String text) {
        return Expression.parse(text).text(context());
    }

    private String calc(String text, ActionContext context) {
        return Expression.parse(text).text(context);
    }

    private void define(String name, Requirement requirement, String owner, DefinitionConflict conflict, Visibility visibility) {
        definitions.defineRequirement(name, requirement, owner, conflict, visibility, warning -> { });
    }

    private void defineAction(String name, Action action, String owner, Visibility visibility) {
        definitions.defineAction(name, action, owner, DefinitionConflict.FAIL, visibility, warning -> { });
    }

    private void defineFunction(String name, final double result, String owner) {
        definitions.defineFunction(name, (context, arguments) -> result, owner, DefinitionConflict.FAIL, Visibility.ALL,
            warning -> { });
    }

    @Test
    void arithmeticFollowsTheUsualPrecedence() {
        assertEquals("14", calc("2 + 3 * 4"));
        assertEquals("20", calc("(2 + 3) * 4"));
        assertEquals("512", calc("2 ^ 3 ^ 2"));
        assertEquals("-1", calc("1 - 2"));
        assertEquals("2.5", calc("5 / 2"));
        assertEquals("1", calc("10 % 3"));
        assertEquals("ab3", calc("'ab' + 3"));
    }

    @Test
    void logicComparisonAndTheConditionOperator() {
        assertEquals("true", calc("1 < 2 && 2 <= 2 && 3 != 4 && !(1 > 2)"));
        assertEquals("yes", calc("5 >= 10 ? 'big' : 5 >= 3 ? 'yes' : 'no'"));
        assertEquals("true", calc("false || true && true"));
        assertEquals("true", calc("'Abc' == 'Abc'"));
    }

    @Test
    void valuesComeFromTheRunAndTextThatLooksLikeANumberIsANumber() {
        ActionContext ctx = context("balance", "250", "rank", "vip", "weird-name", 7);

        assertEquals("25", calc("balance / 10", ctx));
        assertEquals("true", calc("rank == 'vip'", ctx));
        assertEquals("14", calc("{weird-name} * 2", ctx));
        assertEquals("false", calc("unknown_name > 1", ctx));
    }

    @Test
    void functionsAboutThePlayerAndAboutNumbers() {
        assertEquals("true", calc("level() >= 10 && permission('shop.vip') && !permission('admin')"));
        assertEquals("3", calc("min(5, 3, 9)"));
        assertEquals("10", calc("clamp(50, 1, 10)"));
        assertEquals("3.14", calc("round(3.14159, 2)"));
        assertEquals("4", calc("sqrt(16)"));
        assertEquals("3", calc("len('abc')"));
        assertEquals("true", calc("contains(upper('admin team'), 'ADMIN')"));
        assertEquals("yes", calc("if(level() > 5, 'yes', 'no')"));
    }

    @Test
    void aPluginFunctionJoinsTheLanguage() {
        defineFunction("kills", 120.0, "shop/main");

        assertEquals("true", calc("kills() >= 100"));
    }

    @Test
    void mistakesAreReportedWithAPositionAndABadCalculationIsSimplyNotMet() {
        assertThrows(ExpressionException.class, () -> Expression.parse("1 +"));
        assertThrows(ExpressionException.class, () -> Expression.parse("(1 + 2"));
        assertThrows(ExpressionException.class, () -> new ExpressionRequirement("2 $ 3"));
        Verdict verdict = new ExpressionRequirement("'a' * 2").test(context());
        assertTrue(verdict instanceof Verdict.Unmet);
        assertFalse(new ExpressionRequirement("1 / 0 > 1").test(context()).isMet());
    }

    @Test
    void aCalculationInsideATextAndTheSetAction() {
        ActionContext ctx = context("base", 200);
        new SetAction("price", "", "base * 0.9").run(ctx);
        new SetAction("label", "{player} pays {price}").run(ctx);

        assertEquals("180", ctx.format("{= price}"));
        assertEquals("Alice pays 180", ctx.value("label"));
        assertEquals("level 24", ctx.format("level {= level() * 2}"));
        assertEquals("{= 1 +}", ctx.format("{= 1 +}"));
    }

    @Test
    void aDefinedActionIsCalledWithArgumentsThatDoNotLeak() {
        defineAction("greet", context -> {
            log.add(context.format("hello {who}"));
            return ActionResult.Done.INSTANCE;
        }, "shop/main", Visibility.ALL);
        ActionContext ctx = context();

        Map<String, String> first = Collections.singletonMap("who", "{= 1 + 1}");
        Map<String, String> second = Collections.singletonMap("who", "Bob");
        ActionResult result = ctx.execute(Arrays.asList(new CallAction("greet", first), new CallAction("greet", second)));

        assertEquals(ActionResult.Done.INSTANCE, result);
        assertEquals(Arrays.asList("hello 2", "hello Bob"), log);
        assertNull(ctx.value("who"));
        assertTrue(ctx.execute(Collections.singletonList(new CallAction("nope"))) instanceof ActionResult.Failed);
    }

    @Test
    void theNearestNameWinsAndAClashInsideOneModuleFollowsThePolicy() {
        final List<String> warnings = new ArrayList<>();
        define("vip", context -> new Verdict.Unmet("library"), "lib/main", DefinitionConflict.FAIL, Visibility.ALL);
        definitions.defineRequirement("vip", context -> Verdict.Met.INSTANCE, "shop/main", DefinitionConflict.FAIL, Visibility.MODULE,
            warnings::add);

        assertTrue(new RefRequirement("vip").test(context()).isMet());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("vip"));
        assertThrows(IllegalArgumentException.class,
            () -> define("vip", context -> Verdict.Met.INSTANCE, "shop/main", DefinitionConflict.FAIL, Visibility.ALL));
        define("vip", context -> new Verdict.Unmet("kept?"), "shop/main", DefinitionConflict.KEEP, Visibility.ALL);
        assertTrue(new RefRequirement("vip").test(context()).isMet());
        define("vip", context -> new Verdict.Unmet("replaced"), "shop/main", DefinitionConflict.REPLACE, Visibility.ALL);
        assertEquals(new Verdict.Unmet("replaced"), new RefRequirement("vip").test(context()));
        assertEquals(new Verdict.Unmet("requirement 'zzz' is not defined"), new RefRequirement("zzz").test(context()));
    }

    @Test
    void anOwnerInFrontOfANamePicksWhoAnswers() {
        define("vip", context -> new Verdict.Unmet("lib"), "lib/main", DefinitionConflict.FAIL, Visibility.ALL);
        define("vip", context -> Verdict.Met.INSTANCE, "shop/main", DefinitionConflict.FAIL, Visibility.MODULE);
        define("vip", context -> new Verdict.Unmet("sibling"), "shop/other", DefinitionConflict.FAIL, Visibility.PLUGIN);

        assertTrue(new RefRequirement("vip").test(context()).isMet());
        assertTrue(new RefRequirement("~~@vip").test(context()).isMet());
        assertEquals(new Verdict.Unmet("sibling"), new RefRequirement("shop/other@vip").test(context()));
        assertEquals(new Verdict.Unmet("lib"), new RefRequirement("lib@vip").test(context()));
        assertEquals(new Verdict.Unmet("requirement 'bank@vip' is not defined"), new RefRequirement("bank@vip").test(context()));
    }

    @Test
    void moduleAndPluginDefinitionsAreHiddenFromOthersAndGoAwayWithTheOwner() {
        Action done = context -> ActionResult.Done.INSTANCE;
        defineAction("secret", done, "bank/main", Visibility.MODULE);
        defineAction("team", done, "bank/main", Visibility.PLUGIN);
        defineAction("open", done, "bank/main", Visibility.ALL);
        org.pnfolder.pnlibrary.api.scenario.actions.ScenarioLookup view = definitions.view("shop/main");

        assertNull(view.action("secret"));
        assertNull(view.action("team"));
        assertTrue(view.action("open") != null);
        assertTrue(definitions.view("bank/other").action("team") != null);
        definitions.removeOwner("bank/main");
        assertNull(view.action("open"));
    }

    @Test
    void aFunctionCanBeNamedWithItsOwnerInsideAnExpression() {
        defineFunction("kills", 120.0, "shop/main");
        defineFunction("kills", 5.0, "lib/main");

        assertEquals("120", calc("~~@kills()"));
        assertEquals("5", calc("lib/main@kills() + 0"));
        assertEquals("5", calc("lib@kills()"));
    }

    private Action say(final String text) {
        return context -> {
            log.add(context.format(text));
            return ActionResult.Done.INSTANCE;
        };
    }

    @Test
    void anErrorHandlerIsChosenByTheFailureCode() {
        Action failing = context -> new ActionResult.Failed("currency.insufficient-funds", "poor");
        AttemptAction attempt = new AttemptAction(Collections.singletonList(failing), Arrays.asList(
            new ErrorHandler(Collections.singletonList("shop.closed"), Collections.singletonList(say("closed"))),
            new ErrorHandler(Collections.singletonList("currency.*"), Collections.singletonList(say("money: {error_code}")))),
            Collections.singletonList(say("other")));

        context().execute(Collections.singletonList(attempt));
        Action boom = context -> new ActionResult.Failed("x.y", "boom");
        context().execute(Collections.singletonList(new AttemptAction(Collections.singletonList(boom),
            attempt.getHandlers(), attempt.getFail())));

        assertEquals(Arrays.asList("money: currency.insufficient-funds", "other"), log);
    }

    @Test
    void randomPicksByWeight() {
        ActionContext ctx = context();
        RandomOption rare = new RandomOption(0.0001, Collections.singletonList(context -> {
            log.add("rare");
            return ActionResult.Done.INSTANCE;
        }));
        RandomOption common = new RandomOption(1e9, Collections.singletonList(context -> {
            log.add("common");
            return ActionResult.Done.INSTANCE;
        }));
        RandomAction only = new RandomAction(Arrays.asList(rare, common));

        for (int index = 0; index < 20; index++) {
            ctx.execute(Collections.singletonList(only));
        }

        int commons = 0;
        for (String entry : log) {
            commons += entry.equals("common") ? 1 : 0;
        }
        assertTrue(commons >= 19);
    }
}
