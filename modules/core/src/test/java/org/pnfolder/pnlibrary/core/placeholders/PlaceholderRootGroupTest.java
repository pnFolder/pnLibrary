package org.pnfolder.pnlibrary.core.placeholders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.PublishedAnswers;

class PlaceholderRootGroupTest {
    private final List<String> problems = new ArrayList<>();
    private final PlaceholderEngine engine = new PlaceholderEngine(problems::add);

    private Object read(String address) throws Exception {
        return engine.resolve(address).toCompletableFuture().get();
    }

    @Test
    void theArgumentStandsRightUnderThePrefix() throws Exception {
        engine.scope("pncases/keys").register(new TestRootCases());

        assertEquals(5, read("donat"));
        assertEquals(99, read("total"));
    }

    @Test
    void aGroupWordWinsOverTheBareSlot() throws Exception {
        PlaceholderService scope = engine.scope("pncases/keys");
        scope.register(new TestRootCases());
        scope.register(new TestBareKeys());

        assertEquals(99, read("keys.total"));
        assertEquals(5, read("keys.donat"));
        assertEquals(4, read("abcd"));
    }

    @Test
    void twoBareMembersInRootClassesAreRefused() {
        PlaceholderService scope = engine.scope("pncases/keys");
        scope.register(new TestRootCases());

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> scope.register(new TestRootNext()));
        assertTrue(failure.getMessage().contains("only one placeholder may be bare"), failure.getMessage());
    }

    @Test
    void anotherModuleMayHaveItsOwnBareMember() {
        engine.scope("pncases/keys").register(new TestRootCases());
        engine.scope("pnother/main").register(new TestRootNext());
    }

    @Test
    void theBareMemberMayContinueWithAnObject() throws Exception {
        engine.scope("pnstats/main").register(new TestRootNext());

        assertEquals("Alex-wins", read("Alex.wins"));
    }

    @Test
    void thePlaceholderApiNameIsThePrefixAndTheArgument() {
        FakePapi papi = new FakePapi();
        engine.register(papi);
        engine.scope("pncases/keys").registerPublished("pncases", new TestRootCases());

        PublishedAnswers answers = papi.shown.get("pncases");
        assertEquals("5", answers.answer(null, "donat", 0));
        assertEquals("99", answers.answer(null, "total", 0));
    }

    @Test
    void theListShowsTheSlotWithoutAWord() {
        engine.scope("pncases/keys").register(new TestRootCases());

        boolean listed = false;
        for (PlaceholderInfo info : engine.list()) {
            listed |= info.getAddress().startsWith("{");
        }
        assertTrue(listed);
    }

    @Test
    void aRootWordThatIsAlsoAGroupIsReported() {
        PlaceholderService scope = engine.scope("pncases/keys");
        scope.register(new TestBareKeys());
        scope.register(new TestRootKeysWord());

        assertTrue(problems.stream().anyMatch(text -> text.contains("both a placeholder of a root group and a group")), problems.toString());
    }

    @Test
    void aShownWordWithAnUnderscoreIsReported() {
        engine.register(new FakePapi());
        engine.scope("pnbattle/main").registerPublished("pnbattle", new TestRootUnderscore());

        assertTrue(problems.stream().anyMatch(text -> text.contains("'battle_wins'") && text.contains("battle.wins")), problems.toString());
        assertFalse(problems.stream().anyMatch(text -> text.contains("hidden_one")), problems.toString());
    }

    @Test
    void withoutPlaceholderApiTheUnderscoreIsFine() {
        engine.scope("pnbattle/main").register(new TestRootUnderscore());

        assertTrue(problems.isEmpty(), problems.toString());
    }
}
