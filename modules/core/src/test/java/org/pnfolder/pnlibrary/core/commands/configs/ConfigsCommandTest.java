package org.pnfolder.pnlibrary.core.commands.configs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.config.ConfigScope;
import org.pnfolder.pnlibrary.api.config.ManagedConfig;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.config.YamlConfigurations;
import org.pnfolder.pnlibrary.core.commands.engine.CommandRunner;
import org.pnfolder.pnlibrary.core.commands.engine.RecordingSender;
import org.pnfolder.pnlibrary.spi.platform.plugin.PlatformOwnership;

class ConfigsCommandTest {
    @TempDir
    Path folder;

    private final PlatformOwnership platform = new PlatformOwnership() {
        @Override
        public Path ownerDataFolder(@NotNull Object owner) {
            return owner instanceof ConfigOwner ? ((ConfigOwner) owner).folder : null;
        }

        @Override
        public Logger ownerLogger(@NotNull Object owner) {
            return Logger.getLogger("configs-command-test");
        }
    };
    private final YamlConfigurations service = new YamlConfigurations(platform);
    private final PnLibrary library = mock(PnLibrary.class);
    private final CommandDefinition definition;
    private final RecordingSender sender = new RecordingSender();

    ConfigsCommandTest() {
        when(library.getConfigurations()).thenReturn(service);
        definition = new ConfigsCommand(library).definition();
    }

    private ConfigScope scope(String plugin) {
        return service.scope(new ConfigOwner(folder.resolve("plugins").resolve(plugin)), PluginId.of(plugin + "-main"),
                PluginId.of(plugin), ModuleId.of("main"));
    }

    private String run(String... words) {
        return CommandRunner.run(definition, sender, "configs", words);
    }

    @Test
    @DisplayName("the command lists files under their identifiers")
    void theCommandListsFilesUnderTheirIdentifiers() {
        scope("quests").yaml("prizes.yml", PrizeFile.class, PrizeFile::new);

        String text = run();

        assertTrue(text.contains("quests/main:prizes.yml"), text);
        assertTrue(text.contains("PrizeFile"), text);
    }

    @Test
    @DisplayName("info shows where the file lies and never the values")
    void infoShowsWhereTheFileLiesAndNeverTheValues() {
        ManagedConfig<PrizeFile> config = scope("quests").yaml("prizes.yml", PrizeFile.class, PrizeFile::new);
        config.load();

        String text = run("info", "quests/main:prizes.yml");

        assertTrue(text.contains("prizes.yml"), text);
        assertTrue(text.contains("загружен: да"), text);
        assertFalse(text.contains("CoinPrize@"), "values stay out of the report");
    }

    @Test
    @DisplayName("an unknown or malformed identifier is answered, not thrown")
    void anUnknownOrMalformedIdentifierIsAnsweredNotThrown() {
        scope("quests").yaml("prizes.yml", PrizeFile.class, PrizeFile::new);

        assertTrue(run("info", "quests/main:nope.yml").contains("Такого файла нет"));
        assertTrue(run("info", "quests").contains("Неверный адрес"));
    }

    @Test
    @DisplayName("reload reads the file again and reports a broken file without losing the old value")
    void reloadReadsTheFileAgainAndReportsABrokenFileWithoutLosingTheOldValue() throws IOException {
        ManagedConfig<PrizeFile> config = scope("quests").yaml("prizes.yml", PrizeFile.class, PrizeFile::new);
        config.load();
        assertTrue(run("reload", "quests/main:prizes.yml").contains("прочитан заново"));

        Files.write(folder.resolve("plugins/quests/prizes.yml"), "prizes: [unclosed".getBytes(StandardCharsets.UTF_8));
        assertTrue(run("reload", "quests/main:prizes.yml").contains("не прочитан"));
        assertTrue(config.isLoaded());
    }

    @Test
    @DisplayName("validate says when a file is not loaded and when it is fine")
    void validateSaysWhenAFileIsNotLoadedAndWhenItIsFine() {
        ManagedConfig<PrizeFile> config = scope("quests").yaml("prizes.yml", PrizeFile.class, PrizeFile::new);
        assertTrue(run("validate", "quests/main:prizes.yml").contains("не загружен"));

        config.load();
        assertTrue(run("validate", "quests/main:prizes.yml").contains("проблем нет"));
    }

    @Test
    @DisplayName("types lists the words of a polymorphic type and who added them")
    void typesListsTheWordsOfAPolymorphicTypeAndWhoAddedThem() {
        scope("quests").yaml("prizes.yml", PrizeFile.class, PrizeFile::new);
        scope("questgems").type(Prize.class, GemPrize.class).name("gem").into("quests").register();

        String text = run("types", "Prize");

        assertTrue(text.contains("coin"), text);
        assertTrue(text.contains("gem"), text);
        assertTrue(text.contains("questgems"), text);
        int own = 0;
        for (String line : text.split("\n")) {
            own += line.contains("объявлен в самом типе") ? 1 : 0;
        }
        assertEquals(1, own);
        assertTrue(run("types", "Nothing").contains("Такого типа нет"));
    }

    @Test
    @DisplayName("settings lists editable fields and edit changes one and saves the file")
    void settingsListsEditableFieldsAndEditChangesOneAndSavesTheFile() {
        ManagedConfig<TunedFile> config = scope("quests").yaml("tuned.yml", TunedFile.class, TunedFile::new);
        config.load();

        String listing = run("settings", "quests/main:tuned.yml");
        assertTrue(listing.contains("greeting = Привет"), listing);
        assertTrue(listing.contains("нужен перезапуск"), listing);

        String done = run("edit", "quests/main:tuned.yml", "greeting", "Добро пожаловать сюда");
        assertTrue(done.contains("Файл сохранён"), done);
        assertEquals("Добро пожаловать сюда", config.get().greeting);

        String restart = run("edit", "quests/main:tuned.yml", "pool", "20");
        assertTrue(restart.contains("перезапуск"), restart);
    }

    @Test
    @DisplayName("edit answers a wrong value instead of throwing")
    void editAnswersAWrongValueInsteadOfThrowing() {
        scope("quests").yaml("tuned.yml", TunedFile.class, TunedFile::new).load();

        assertTrue(run("edit", "quests/main:tuned.yml", "pool", "many").contains("Не изменено"));
        assertTrue(run("edit", "quests/main:tuned.yml", "nothing", "1").contains("Такой настройки нет"));
    }
}
