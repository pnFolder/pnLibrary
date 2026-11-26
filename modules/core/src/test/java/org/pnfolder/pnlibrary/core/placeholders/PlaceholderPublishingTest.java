package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.core.placeholders.library.LibraryPlaceholders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.PublishedAnswers;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;

class PlaceholderPublishingTest {
    private final PlaceholderEngine engine = new PlaceholderEngine(problem -> { });

    private PlaceholderService module() {
        PlaceholderService module = engine.scope("pnclans/war");
        module.register(new TestClanValues());
        module.formats(new TestClanFormats());
        return module;
    }

    private Object read(String address) throws Exception {
        return engine.resolve(address, ViewerInfo.none()).toCompletableFuture().get();
    }

    @Test
    void placeholdersWithAPrefixAreShownToPlaceholderAPIAndAnsweredFromThere() {
        FakePapi papi = new FakePapi();
        engine.register(papi);
        PlaceholderService module = engine.scope("pnclans/war");
        module.registerPublished("pnclans", new TestClanValues());

        PublishedAnswers answers = papi.shown.get("pnclans");
        assertEquals("340", answers.answer(null, "clan_war_score", 0));
        assertEquals("30", answers.answer(null, "clan_top_3", 0));
        assertNull(answers.answer(null, "nobody_here", 0));
        PlaceholderInfo score = null;
        for (PlaceholderInfo info : engine.list()) {
            if (info.getAddress().equals("clan.war.score")) {
                score = info;
            }
        }
        assertTrue(score.getPublished().contains("pnclans_clan_war_score"));

        module.close();

        assertTrue(papi.shown.isEmpty(), "closing the module withdraws the prefix");
    }

    @Test
    void anAdapterThatComesLaterStillGetsThePrefixesAlreadyRegistered() {
        engine.scope("pnclans/war").registerPublished("pnclans", new TestClanValues());
        FakePapi papi = new FakePapi();

        engine.register(papi);

        assertTrue(papi.shown.containsKey("pnclans"));
    }

    @Test
    void textsGetTheTokensOfTheOutsideSystemFilled() throws Exception {
        engine.register(new FakePapi());
        module();

        String text = engine.render("{clan.tag} on %server_name%").toCompletableFuture().get();

        assertEquals("WLF on Lobby", text);
    }

    @Test
    void aModuleWithoutPlaceholderAPIKeepsItsPrefixToItself() {
        FakePapi papi = new FakePapi();
        engine.register(papi);

        engine.scope("pnclans/war", false).registerPublished("pnclans", new TestClanValues());

        assertTrue(papi.shown.isEmpty());
    }

    @Test
    void theLibraryPlaceholdersReadItsNumbers() throws Exception {
        PlatformIdentity identity = Mockito.mock(PlatformIdentity.class);
        engine.scope("pnlibrary/main").register(
                new LibraryPlaceholders(identity, 0L, () -> 3, () -> Collections.singletonMap("errors", 7L)));

        assertEquals(3, read("pnlibrary.plugins"));
        assertEquals(7L, read("pnlibrary.stats.errors"));
        assertEquals(0L, read("pnlibrary.stats.warnings"));
    }
}
