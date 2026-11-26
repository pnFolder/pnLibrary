package org.pnfolder.pnlibrary.core.placeholders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentAdapters;
import org.pnfolder.pnlibrary.api.placeholders.info.FormatInfo;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.registry.RegistryEntry;

class PlaceholderServiceTest {
    private final List<String> problems = new ArrayList<String>();
    private final PlaceholderEngine engine = new PlaceholderEngine(problems::add);

    private PlaceholderService module() {
        PlaceholderService module = engine.scope("pnclans/war");
        module.register(new TestClanValues());
        module.formats(new TestClanFormats());
        return module;
    }

    private Object read(String address) throws Exception {
        return read(address, ViewerInfo.none());
    }

    private Object read(String address, ViewerInfo viewer) throws Exception {
        return engine.resolve(address, viewer).toCompletableFuture().get();
    }

    @Test
    void theFormatRegistryListsBuiltInFormatsOfTheLibraryAndTheFormatsOfAModuleWithTheirOwner() {
        List<RegistryEntry<FormatInfo>> formats = module().formatRegistry().entries();

        String roundOwner = null;
        boolean fromModule = false;
        for (RegistryEntry<FormatInfo> format : formats) {
            if (format.getKey().equals("round")) {
                roundOwner = format.getOwner();
            }
            fromModule |= format.getOwner().equals("pnclans/war");
        }
        assertEquals("pnLibrary", roundOwner);
        assertTrue(fromModule);
    }

    @Test
    void plainValuesBranchesAndPropertiesArePlaceholders() throws Exception {
        module();
        ViewerInfo player = ViewerInfo.of(UUID.randomUUID());

        assertEquals(12, read("clan.level", player));
        assertNull(read("clan.level"), "without a player the method ends quietly");
        assertEquals(340, read("clan.war.score"));
        assertEquals(2, read("{clan.war.wins}"));
        assertEquals("WLF", read("clan.tag"));
        assertEquals("owls", read("clan.owls"), "a property without a field works too");
    }

    @Test
    void hiddenMembersAndUnknownAddressesGiveNothing() throws Exception {
        module();

        assertNull(read("clan.secret"));
        assertNull(read("clan.war.secretkey"));
        assertNull(read("nobody.here"));
        assertNull(read("clan"));
    }

    @Test
    void argumentsArriveConvertedAndChecked() throws Exception {
        module();

        assertEquals(30, read("clan.top.3"));
        assertNull(read("clan.top.500"), "outside the range");
        assertNull(read("clan.top.x"), "not a number");
        assertEquals(47, read("clan.kills.Alex.week"));
        assertNull(read("clan.kills.Alex.year"), "a word outside the list");
        assertEquals("WEEK", read("clan.period.week"));
        assertNull(read("clan.period.year"));
        assertEquals("Alex-week", read("clan.query.Alex.week"));
    }

    @Test
    void theMethodSeesTheTextAndMeStandsForTheViewer() throws Exception {
        module();
        UUID id = UUID.randomUUID();

        assertEquals("Bob", read("clan.friend.Bob"));
        assertEquals(id.toString(), read("clan.mine.me", ViewerInfo.of(id)));
        assertEquals("Alex", read("clan.mine.Alex", ViewerInfo.of(id)));
    }

    @Test
    void formatsOfTheTextOfTheMethodAndOfAModule() throws Exception {
        module();

        assertEquals("WOLF", read("clan.title"));
        assertEquals("wolf", read("clan.title|lower"), "a format in the text replaces the default");
        assertEquals("wolf", read("clan.title|raw"), "raw switches the default format off");
        assertEquals("00010", read("clan.top.1|pad0:5"));
        assertEquals("30/ru", read("clan.top.3|lang", new ViewerInfo(null, "ru")));
    }

    @Test
    void builtInFormatsFollowTheLanguageOfTheViewer() throws Exception {
        module();
        ViewerInfo ru = new ViewerInfo(null, "ru");
        ViewerInfo en = new ViewerInfo(null, "en");

        assertEquals("1 250 000", read("clan.money|group", ru));
        assertEquals("1,250,000", read("clan.money|group", en));
        assertEquals("1,3M", read("clan.money|short", ru));
        assertEquals("1.3M", read("clan.money|short", en));
        assertEquals("1h 30m", read("clan.age.1h30m|left", en));
        assertEquals("1ч 30м", read("clan.age.1h30m|left", ru));
        assertEquals("01:30:00", read("clan.age.1h30m|hms"));
        assertEquals("есть", read("clan.top.1|ifpos:есть,нет"));
        assertEquals("[WLF]", read("clan.tag|wrap:[,]"));
        assertEquals("WL…", read("clan.tag|cut:2"));
    }

    @Test
    void aFormatThatDoesNotFitLeavesTheValueAndReportsIt() throws Exception {
        module();

        assertEquals("WLF", read("clan.tag|short"));
        boolean reported = false;
        for (String problem : problems) {
            reported |= problem.contains("short");
        }
        assertTrue(reported, problems.toString());
    }

    @Test
    void renderFillsTheTextsAndLeavesUnknownAddresses() throws Exception {
        module();
        String text = engine.render("Level {clan.war.score|group}, tag {clan.tag}, {nobody.here}!").toCompletableFuture().get();

        assertEquals("Level 340, tag WLF, {nobody.here}!", text);
    }

    @Test
    void aPartWithADotOrABarIsWrittenInQuotesOrWithATildeCode() throws Exception {
        module();
        List<String> words = Addresses.parse("clan.friend.a~7Cb").getWords();

        assertEquals("a.b", read("clan.friend.\"a.b\""));
        assertEquals("a|b", words.get(words.size() - 1));
    }

    @Test
    void closingTheModuleReleasesItsPlaceholdersAndFormats() throws Exception {
        PlaceholderService module = module();
        assertEquals(340, read("clan.war.score"));

        module.close();

        assertNull(read("clan.war.score"));
        assertTrue(engine.list().isEmpty());
    }

    @Test
    void theListShowsEveryPlaceholderWithItsOwnerAndDescription() {
        module();
        List<PlaceholderInfo> list = engine.list();

        PlaceholderInfo level = null;
        boolean hiddenListed = false;
        for (PlaceholderInfo info : list) {
            if (info.getAddress().equals("clan.level")) {
                level = info;
            }
            hiddenListed |= info.getAddress().contains("secret");
        }
        assertEquals("Clan level", level.getDescription());
        assertEquals("pnclans/war", level.getOwner());
        assertTrue(!hiddenListed, "hidden ones are not listed");
    }

    @Test
    void anArgumentTypeWithoutAnAdapterIsReportedWhereItIsWritten() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> engine.scope("x/y").register(new TestNeedsUnknown()));
        assertTrue(String.valueOf(failure.getMessage()).contains("Thread"), failure.getMessage());
    }

    @Test
    void aPluginCanTeachTheLibraryItsOwnArgumentType() throws Exception {
        ArgumentAdapters.register(TestClan.class, "clan", text -> text.equals("Wolves") ? new TestClan(text) : null);
        try {
            engine.scope("x/own").register(new TestOwnType());

            assertEquals(6, read("own.clanof.Wolves"));
            assertNull(read("own.clanof.Owls"));
        } finally {
            ArgumentAdapters.unregister(TestClan.class);
        }
    }

    @Test
    void valuesHandedOverByTheCallerWinAndTakeFormats() throws Exception {
        module();
        Map<String, Object> values = new HashMap<String, Object>();
        values.put("amount", 1500);
        values.put("clan.tag", "mine");

        assertEquals(1500, engine.resolve("amount", ViewerInfo.none(), values).toCompletableFuture().get());
        assertEquals("1,500", engine.resolve("{amount|group}", ViewerInfo.none(), values).toCompletableFuture().get());
        assertEquals("mine", engine.resolve("clan.tag", ViewerInfo.none(), values).toCompletableFuture().get());
        assertEquals("a 1500 b", engine.render("a {amount} b", ViewerInfo.none(), values).toCompletableFuture().get());
    }

    @Test
    void aPlaceholderWithoutAClassCanBeAddedAndRemoved() throws Exception {
        PlaceholderService module = engine.scope("shop/main");
        AutoCloseable handle = module.value("shop.coins", "Coins of the player", viewer -> viewer.hasPlayer() ? 5 : null);

        assertEquals(5, read("shop.coins", ViewerInfo.of(UUID.randomUUID())));
        assertNull(read("shop.coins"));
        assertTrue(module.contains("shop.coins"));
        boolean listed = false;
        for (PlaceholderInfo info : engine.list()) {
            listed |= info.getAddress().equals("shop.coins") && info.getDescription().equals("Coins of the player");
        }
        assertTrue(listed);

        handle.close();

        assertTrue(!module.contains("shop.coins"));
        assertNull(read("shop.coins", ViewerInfo.of(UUID.randomUUID())));
    }
}
