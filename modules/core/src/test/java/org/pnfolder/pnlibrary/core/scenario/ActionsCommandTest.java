package org.pnfolder.pnlibrary.core.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.registry.Visibility;
import org.pnfolder.pnlibrary.core.placeholders.UsageVariant;
import org.pnfolder.pnlibrary.core.placeholders.UsageVariants;

class ActionsCommandTest {
    private static List<String> texts(List<UsageVariant> variants) {
        List<String> texts = new ArrayList<>();
        for (UsageVariant variant : variants) {
            texts.add(variant.getText());
        }
        return texts;
    }

    @Test
    void anActionIsWrittenWithEveryOwnerFormItsVisibilityAllows() {
        List<String> shared = texts(DefinitionUsage.of(new ScenarioDefinitionInfo("action", "give-kit", "shop/main", "all")));

        assertEquals(Arrays.asList(
            "{ type: call, name: 'give-kit' }",
            "{ type: call, name: 'shop/main@give-kit' }",
            "{ type: call, name: 'shop@give-kit' }",
            "{ type: call, name: '~~@give-kit' }",
            "{ type: call, name: '~@give-kit' }"), shared);
        List<String> own = texts(DefinitionUsage.of(new ScenarioDefinitionInfo("requirement", "vip", "shop/main", "module")));
        for (String text : own) {
            assertFalse(text.contains("shop@vip") || text.contains("'~@vip'"));
        }
        assertTrue(texts(DefinitionUsage.of(new ScenarioDefinitionInfo("function", "kills", "shop/main", "all"))).contains("~~@kills()"));
    }

    @Test
    void aPlaceholderShowsItsFormsAndHidesTheOnesThatCannotWork() {
        List<String> open = texts(UsageVariants.of(new PlaceholderInfo("shop.price", "", "shop/main", "Int", null,
            Collections.emptyList(), Visibility.ALL)));
        assertTrue(open.contains("{shop/main@shop.price}"));
        assertTrue(open.contains("{shop@shop.price}"));
        assertTrue(open.contains("{~@shop.price}"));

        List<String> hidden = texts(UsageVariants.of(new PlaceholderInfo("shop.secret", "", "shop/main", "Int", null,
            Collections.emptyList(), Visibility.MODULE)));
        assertTrue(hidden.contains("{~~@shop.secret}"));
        assertFalse(hidden.contains("{shop@shop.secret}"));
        assertFalse(hidden.contains("{~@shop.secret}"));
    }
}
