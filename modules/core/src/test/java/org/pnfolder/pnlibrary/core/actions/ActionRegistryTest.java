package org.pnfolder.pnlibrary.core.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.registry.RegistryEntry;
import org.pnfolder.pnlibrary.api.registry.Visibility;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.DefinitionConflict;
import org.pnfolder.pnlibrary.core.plugin.module.definition.ScenarioDefinitions;

class ActionRegistryTest {
    private final ScenarioDefinitions definitions = new ScenarioDefinitions();
    private final Action action = context -> {
        throw new IllegalStateException("not run");
    };

    private static List<String> keys(List<RegistryEntry<Action>> entries) {
        List<String> keys = new ArrayList<>();
        for (RegistryEntry<Action> entry : entries) {
            keys.add(entry.getKey());
        }
        return keys;
    }

    @Test
    void aModuleSeesItsOwnAndPublicActionsNotThePrivateActionsOfAnotherPlugin() {
        definitions.defineAction("give-kit", action, "shop/main", DefinitionConflict.FAIL, Visibility.ALL, warning -> { });
        definitions.defineAction("secret", action, "shop/main", DefinitionConflict.FAIL, Visibility.MODULE, warning -> { });

        List<String> own = keys(definitions.actionEntries("shop/main"));
        List<RegistryEntry<Action>> other = definitions.actionEntries("clans/main");

        assertEquals(Arrays.asList("give-kit", "secret"), own);
        assertEquals(Collections.singletonList("give-kit"), keys(other));
        assertEquals("shop/main", other.get(0).getOwner());
    }

    @Test
    void closingADefinitionRemovesItFromTheList() throws Exception {
        AutoCloseable handle = definitions.defineAction("temp", action, "shop/main", DefinitionConflict.FAIL,
            Visibility.ALL, warning -> { });
        handle.close();

        assertEquals(Collections.emptyList(), keys(definitions.actionEntries("shop/main")));
    }
}
