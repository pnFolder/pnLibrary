package org.pnfolder.pnlibrary.core.placeholders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.registry.Visibility;

class PlaceholderVisibilityTest {
    private final List<String> problems = new ArrayList<String>();
    private final PlaceholderEngine engine = new PlaceholderEngine(problems::add);

    private String read(String text) throws Exception {
        return engine.render(text, ViewerInfo.none()).toCompletableFuture().get();
    }

    private static String ask(PlaceholderService module, String text) throws Exception {
        return module.render(text, ViewerInfo.none()).toCompletableFuture().get();
    }

    @Test
    void moduleVisibilityHidesAPlaceholderFromOtherModulesAndOutsiders() {
        PlaceholderService mine = engine.scope("shop/main");
        mine.value("shop.secret", "", null, Visibility.MODULE, viewer -> 1);

        assertTrue(mine.contains("shop.secret"));
        assertFalse(engine.scope("shop/other").contains("shop.secret"));
        assertFalse(engine.contains("shop.secret"));
    }

    @Test
    void visibilityOnAPropertyHidesOnlyThatPlaceholderAndTheOperatorTestStillSeesIt() throws Exception {
        engine.scope("shop/main").registerPublished(null, new ShopHolder());

        assertTrue(engine.contains("shop.price"));
        assertFalse(engine.contains("shop.counter"));
        assertTrue(engine.scope("shop/main").contains("shop.counter"));
        assertEquals("2", engine.resolveAll("shop.counter").toCompletableFuture().get().toString());
    }

    @Test
    void pluginVisibilityReachesTheModulesOfTheSamePluginOnly() {
        engine.scope("shop/main").value("shop.price", "", null, Visibility.PLUGIN, viewer -> 1);

        assertTrue(engine.scope("shop/other").contains("shop.price"));
        assertFalse(engine.scope("bank/main").contains("shop.price"));
        assertFalse(engine.contains("shop.price"));
    }

    @Test
    void theNearestPlaceholderWinsAndTheClashIsReported() throws Exception {
        engine.scope("bank/main").value("shop.price", viewer -> "far");
        engine.scope("shop/main").value("shop.price", viewer -> "near");

        assertEquals("near", ask(engine.scope("shop/main"), "{shop.price}"));
        boolean reported = false;
        for (String problem : problems) {
            reported |= problem.contains("shop.price");
        }
        assertTrue(reported);
    }

    @Test
    void aClosedModuleTakesItsConvertersAndPlaceholdersAway() throws Exception {
        PlaceholderService module = engine.scope("shop/main");
        module.value("shop.coins", viewer -> 1);
        module.converter(Thread.class, new ArgumentConverter<Thread>() {
            @Override
            public String getLabel() {
                return "thread";
            }

            @Override
            public Thread read(ArgumentInput input) {
                return null;
            }
        });
        module.close();

        assertFalse(engine.contains("shop.coins"));
        assertEquals("{shop.coins}", read("{shop.coins}"));
    }

    @Test
    void anOwnerInFrontOfTheAddressPicksWhoAnswers() throws Exception {
        engine.scope("bank/main").value("shop.price", viewer -> "bank");
        PlaceholderService shop = engine.scope("shop/main");
        shop.value("shop.price", viewer -> "shop");
        PlaceholderService other = engine.scope("shop/other");

        assertEquals("bank", ask(shop, "{bank/main@shop.price}"));
        assertEquals("shop", ask(other, "{shop@shop.price}"));
        assertEquals("shop", ask(shop, "{~~@shop.price}"));
        assertEquals("{~~@shop.price}", ask(other, "{~~@shop.price}"));
        assertEquals("shop", ask(other, "{~@shop.price}"));
        assertEquals("{~~@shop.price}", read("{~~@shop.price}"));
    }

    @Test
    void aFormatCanBePickedByItsOwner() throws Exception {
        engine.scope("shop/main").value("shop.name", viewer -> "abc");
        PlaceholderService module = engine.scope("a/main");

        assertEquals("ABC", ask(module, "{shop.name|~~~@upper}"));
        assertEquals("abc", ask(module, "{shop.name|bank@upper}"));
    }

    @Test
    void anOwnerOfThePlaceholderAndOfTheFormatWorkTogether() throws Exception {
        engine.scope("shop/main").value("shop.name", viewer -> "abc");
        PlaceholderService module = engine.scope("a/main");

        assertEquals("ABC", ask(module, "{shop/main@shop.name|~~~@upper}"));
    }

    @Test
    void aPlaceholderCanBeInsideAnotherOne() throws Exception {
        PlaceholderService shop = engine.scope("shop/main");
        shop.value("shop.player", viewer -> "alex");
        shop.value("shop.alex.level", viewer -> 7);

        assertEquals("7", ask(shop, "{shop.{shop.player}.level}"));
        assertEquals("[{shop.{shop.nobody}.level}]", ask(shop, "[{shop.{shop.nobody}.level}]"));
    }

    @Test
    void placeholdersInsidePlaceholdersGoDeepInEveryPlace() throws Exception {
        PlaceholderService shop = engine.scope("shop/main");
        shop.value("shop.a", viewer -> "b");
        shop.value("shop.b", viewer -> "c");
        shop.value("shop.c", viewer -> "d");
        shop.value("shop.d", viewer -> 4);
        shop.value("shop.owner", viewer -> "shop/main");
        shop.value("shop.width", viewer -> 5);
        shop.value("shop.name", viewer -> "ab");
        shop.value("shop.one.x.two.y", viewer -> "both");
        shop.value("shop.x", viewer -> "x");
        shop.value("shop.y", viewer -> "y");
        shop.value("shop.text", viewer -> "ab");

        assertEquals("4", ask(shop, "{shop.{shop.{shop.{shop.a}}}}"));
        assertEquals("both", ask(shop, "{shop.one.{shop.x}.two.{shop.y}}"));
        assertEquals("ab", ask(shop, "{shop.name|~~~@upper}").toLowerCase(Locale.ROOT));
        assertEquals("ab", ask(shop, "{{shop.owner}@shop.text}"));
    }

    @Test
    void tooDeepPlaceholdersAreRefusedAndReported() throws Exception {
        PlaceholderService shop = engine.scope("shop/main");
        shop.value("shop.a", viewer -> 1);
        String text = String.join("", Collections.nCopies(40, "{shop.")) + "a" + String.join("", Collections.nCopies(40, "}"));

        assertEquals(text, ask(shop, text));
        boolean reported = false;
        for (String problem : problems) {
            reported |= problem.contains("inside each other");
        }
        assertTrue(reported);
    }

    @Test
    void aValueWithBracesIsNotReadAgain() throws Exception {
        PlaceholderService shop = engine.scope("shop/main");
        shop.value("shop.curly", viewer -> "{shop.curly}");

        assertEquals("{shop.curly}", ask(shop, "{shop.curly}"));
        assertEquals("{shop.x.{shop.curly}}", ask(shop, "{shop.x.{shop.curly}}"));
    }

    @Test
    void valuesOfARunAreSkippedWhenAnOwnerIsWritten() throws Exception {
        PlaceholderService shop = engine.scope("shop/main");
        Map<String, Object> values = new HashMap<String, Object>();
        values.put("item", "local");
        shop.value("shop.item", viewer -> "registered");

        assertEquals("local", shop.render("{item}", ViewerInfo.none(), values).toCompletableFuture().get());
        assertEquals("{~~@item}", shop.render("{~~@item}", ViewerInfo.none(), values).toCompletableFuture().get());
        assertEquals("registered", shop.render("{~~@shop.item}", ViewerInfo.none(), values).toCompletableFuture().get());
    }
}
