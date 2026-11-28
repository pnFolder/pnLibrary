package org.pnfolder.pnlibrary.core.placeholders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.PublishedAnswers;

class PlaceholderBareSlotTest {
    private final PlaceholderEngine engine = new PlaceholderEngine(problem -> { });

    private Object read(String address) throws Exception {
        return engine.resolve(address).toCompletableFuture().get();
    }

    @Test
    void theArgumentStandsRightAfterTheGroup() throws Exception {
        engine.scope("test/bare").register(new TestBareKeys());

        assertEquals(5, read("keys.donat"));
    }

    @Test
    void aWordOfTheMethodWinsOverTheBareSlot() throws Exception {
        engine.scope("test/bare").register(new TestBareKeys());

        assertEquals(99, read("keys.total"));
    }

    @Test
    void aBareMemberMayContinueWithAnObject() throws Exception {
        engine.scope("test/bare").register(new TestBarePlayer());

        assertEquals("Alex-wins", read("player.Alex.wins"));
    }

    @Test
    void twoBareMembersOnOneLevelAreRefused() {
        PlaceholderService scope = engine.scope("test/bare");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> scope.register(new TestBareTwice()));
        assertTrue(failure.getMessage().contains("only one placeholder may be bare"), failure.getMessage());
    }

    @Test
    void thePlaceholderApiNameUsesTheSameParts() {
        FakePapi papi = new FakePapi();
        engine.register(papi);
        engine.scope("pncases/keys").registerPublished("pncases", new TestBareKeys());

        PublishedAnswers answers = papi.shown.get("pncases");
        assertEquals("5", answers.answer(null, "keys_donat", 0));
        assertEquals("99", answers.answer(null, "keys_total", 0));
    }

    @Test
    void theListShowsTheSlotWithoutAWord() {
        engine.scope("test/bare").register(new TestBareKeys());

        boolean listed = false;
        for (PlaceholderInfo info : engine.list()) {
            listed |= info.getAddress().startsWith("keys.{");
        }
        assertTrue(listed);
    }
}
