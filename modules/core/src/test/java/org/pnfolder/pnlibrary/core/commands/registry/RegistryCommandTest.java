package org.pnfolder.pnlibrary.core.commands.registry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.registry.Registry;
import org.pnfolder.pnlibrary.api.registry.RegistryEntry;
import org.pnfolder.pnlibrary.api.registry.Visibility;
import org.pnfolder.pnlibrary.core.commands.engine.CommandRunner;
import org.pnfolder.pnlibrary.core.commands.engine.RecordingSender;

class RegistryCommandTest {
    private final RecordingSender sender = new RecordingSender();
    private final CommandDefinition definition = RegistryCommand.create(registries());

    private static List<Registry<?>> registries() {
        List<Registry<?>> registries = new ArrayList<Registry<?>>();
        registries.add(registry("placeholders", new RegistryEntry<String>("shop.coins", "x", "shop/main", Visibility.PLUGIN, "Монеты")));
        registries.add(registry("services", new RegistryEntry<String>("java.lang.Runnable", "y", "pnLibrary")));
        return registries;
    }

    private static Registry<String> registry(final String name, final RegistryEntry<String> entry) {
        return new Registry<String>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public List<RegistryEntry<String>> entries() {
                return Arrays.asList(entry);
            }
        };
    }

    private String run(String... words) {
        return CommandRunner.run(definition, sender, "registry", words);
    }

    @Test
    @DisplayName("without words it lists the kinds with their sizes")
    void withoutWordsItListsTheKindsWithTheirSizes() {
        String text = run();

        assertTrue(text.contains("placeholders  1") && text.contains("services  1"), text);
    }

    @Test
    @DisplayName("a kind shows owner, visibility and description of each entry")
    void aKindShowsOwnerVisibilityAndDescriptionOfEachEntry() {
        String text = run("placeholders");

        assertTrue(text.contains("shop.coins  shop/main  [plugin]  Монеты"), text);
    }

    @Test
    @DisplayName("find searches every kind by key or owner")
    void findSearchesEveryKindByKeyOrOwner() {
        String text = run("find", "pnlibrary");

        assertTrue(text.contains("services  java.lang.Runnable"), text);
    }

    @Test
    @DisplayName("an unknown kind names the ones that exist")
    void anUnknownKindNamesTheOnesThatExist() {
        assertTrue(run("nothing").contains("placeholders, services"));
    }
}
