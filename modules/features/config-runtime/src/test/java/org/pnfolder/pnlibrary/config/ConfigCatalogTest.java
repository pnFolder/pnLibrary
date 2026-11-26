package org.pnfolder.pnlibrary.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.config.ConfigId;
import org.pnfolder.pnlibrary.api.config.ConfigInfo;
import org.pnfolder.pnlibrary.api.config.ConfigOptions;
import org.pnfolder.pnlibrary.api.config.ConfigRoot;
import org.pnfolder.pnlibrary.api.config.ConfigScope;
import org.pnfolder.pnlibrary.api.config.ConfigTypeInfo;
import org.pnfolder.pnlibrary.api.config.ManagedConfig;
import org.pnfolder.pnlibrary.api.config.UnknownTypePolicy;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/** Several plugins in one server folder: {@code plugins/<name>/} for each, so {@code PLUGINS} and {@code SERVER} roots exist. */
class ConfigCatalogTest {
    @TempDir
    Path server;

    private final ConfigOptions skipping = ConfigOptions.builder().unknownTypes(UnknownTypePolicy.SKIP).build();

    private Path plugins() {
        return server.resolve("plugins");
    }

    private ConfigScope scope(YamlConfigurations service, String plugin) throws IOException {
        return scope(service, plugin, "main");
    }

    private ConfigScope scope(YamlConfigurations service, String plugin, String module) throws IOException {
        Path folder = plugins().resolve(plugin);
        Files.createDirectories(folder);
        return service.scope(new FakeConfigOwner(folder.toFile()), PluginId.of(plugin + "-" + module), PluginId.of(plugin),
                ModuleId.of(module));
    }

    private void write(String plugin, String name, String text) throws IOException {
        Path file = plugins().resolve(plugin).resolve(name);
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static YamlConfigurations newService() {
        return new YamlConfigurations(FakeOwnerPlatform.INSTANCE);
    }

    private static ManagedConfig<AwardsFile> awards(ConfigScope scope, String path) {
        return scope.yaml(path, AwardsFile.class, AwardsFile::new);
    }

    private static ManagedConfig<AwardsFile> awards(ConfigScope scope, String path, ConfigOptions options) {
        return scope.yaml(path, AwardsFile.class, AwardsFile::new, options);
    }

    private static List<String> ids(List<ConfigInfo> infos) {
        List<String> ids = new ArrayList<>();
        for (ConfigInfo info : infos) {
            ids.add(info.getId().toString());
        }
        return ids;
    }

    // ── Identifiers and lookup ────────────────────────────────────────────────────────────────

    @Test
    void registeredFilesAreListedUnderPluginModuleAndPath() throws IOException {
        try (YamlConfigurations service = newService()) {
            ConfigScope main = scope(service, "quests");
            ConfigScope extra = scope(service, "quests", "extra");
            awards(scope(service, "shop"), "prices.yml");
            ManagedConfig<AwardsFile> awards = awards(main, "awards.yml");
            awards(extra, "rewards/daily.yml");

            assertEquals(Arrays.asList("shop/main:prices.yml", "quests/main:awards.yml", "quests/extra:rewards/daily.yml"),
                    ids(service.all()));
            assertEquals(2, service.find("quests").size());
            assertEquals(Collections.singletonList("quests/main:awards.yml"), ids(service.find("quests/main")));
            assertEquals(1, service.find("quests/extra:rewards/daily.yml").size());
            assertTrue(service.find("nobody").isEmpty());
            assertEquals(awards.getId(), ConfigId.of("quests", "main", "awards.yml"));
            assertSame(awards, service.get("quests/main:awards.yml"));
            assertSame(awards, main.get("awards.yml"));
            assertNull(service.get("quests/main:missing.yml"));
            assertEquals(AwardsFile.class, service.require("quests/main:awards.yml").getInfo().getModelType());
            assertEquals(3, service.byModel(AwardsFile.class).size());
            assertTrue(service.byModel(String.class).isEmpty());
        }
    }

    @Test
    void aScopeListsOnlyItsOwnFilesAndRequireNamesTheRegisteredOnes() throws IOException {
        try (YamlConfigurations service = newService()) {
            ConfigScope main = scope(service, "quests");
            awards(scope(service, "quests", "extra"), "other.yml");
            awards(main, "awards.yml");

            List<String> paths = new ArrayList<>();
            for (ConfigInfo info : main.list()) {
                paths.add(info.getPath());
            }
            assertEquals(Collections.singletonList("awards.yml"), paths);
            assertEquals("quests", main.getPlugin().getValue());
            assertEquals("main", main.getModule().getValue());
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> main.require("nope.yml"));
            assertTrue(String.valueOf(error.getMessage()).contains("awards.yml"), error.getMessage());
            assertThrows(IllegalStateException.class, () -> service.require("quests/main:nope.yml"));
        }
    }

    @Test
    void infoDescribesAFileWithoutValuesAndFollowsItsLife() throws IOException {
        try (YamlConfigurations service = newService()) {
            ManagedConfig<AwardsFile> config = awards(scope(service, "quests"), "awards.yml");

            ConfigInfo before = config.getInfo();
            assertFalse(before.getExists());
            assertFalse(before.isLoaded());
            assertNull(before.getLoadedAt());
            config.load();
            ConfigInfo after = config.getInfo();
            assertTrue(after.getExists());
            assertTrue(after.isLoaded());
            assertNotNull(after.getLoadedAt());
            assertEquals(plugins().resolve("quests").resolve("awards.yml"), after.getFile());
            config.unload();
            assertFalse(config.getInfo().isLoaded());
        }
    }

    @Test
    void closingAScopeRemovesItsFilesFromTheCatalog() throws IOException {
        try (YamlConfigurations service = newService()) {
            ConfigScope scope = scope(service, "quests");
            awards(scope, "awards.yml");
            assertEquals(1, service.all().size());
            scope.close();
            assertTrue(service.all().isEmpty());
        }
    }

    // ── Types added to another plugin's files ─────────────────────────────────────────────────

    @Test
    void aPluginTeachesAnotherPluginsFileANewWordAndTheFileReadsIt() throws IOException {
        write("quests", "awards.yml", "awards:\n  - type: money\n    amount: 7\n  - type: item\n    material: DIAMOND\n    count: 3\n");
        try (YamlConfigurations service = newService()) {
            ManagedConfig<AwardsFile> awards = awards(scope(service, "quests"), "awards.yml");
            scope(service, "questitems").type(Award.class, ItemAward.class).name("item").into("quests/main:awards.yml").register();

            assertEquals(Arrays.asList(new MoneyAward(7), new ItemAward("DIAMOND", 3)), awards.load().getValue().awards);
        }
    }

    @Test
    void withoutTheWordTheFileFailsByDefaultAndNamesTheElement() throws IOException {
        write("quests", "awards.yml", "awards:\n  - type: item\n    material: DIAMOND\n");
        try (YamlConfigurations service = newService()) {
            ManagedConfig<AwardsFile> awards = awards(scope(service, "quests"), "awards.yml");

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, awards::load);
            assertTrue(String.valueOf(error.getMessage()).contains("item"), error.getMessage());
        }
    }

    @Test
    void skippingKeepsTheElementInTheFileAndReadsItAgainWhenTheWordAppears() throws IOException {
        String text = "awards:\n  - type: money\n    amount: 7\n  - type: item\n    material: DIAMOND\n    count: 3\n";
        write("quests", "awards.yml", text);
        try (YamlConfigurations service = newService()) {
            ManagedConfig<AwardsFile> awards = awards(scope(service, "quests"), "awards.yml", skipping);

            assertEquals(Collections.singletonList(new MoneyAward(7)), awards.load().getValue().awards);
            assertEquals(1, awards.getInfo().getSkippedUnknownTypes().size());
            assertTrue(read(plugins().resolve("quests").resolve("awards.yml")).contains("DIAMOND"), "the element stays in the file");
            // Saving would drop the skipped element from the file, so it is refused.
            assertThrows(IllegalStateException.class, awards::save);
            assertThrows(IllegalStateException.class, () -> awards.update(value -> value));

            scope(service, "questitems").type(Award.class, ItemAward.class).name("item").into("quests/main:awards.yml").register();

            // Registering the word read the file again by itself.
            assertEquals(Arrays.asList(new MoneyAward(7), new ItemAward("DIAMOND", 3)), awards.get().awards);
            assertTrue(awards.getInfo().getSkippedUnknownTypes().isEmpty());
            awards.save();
            assertTrue(read(plugins().resolve("quests").resolve("awards.yml")).contains("DIAMOND"));
        }
    }

    @Test
    void aWordAddedToAModuleCoversItsFilesAndNoOtherModule() throws IOException {
        String text = "awards:\n  - type: item\n";
        write("quests", "a.yml", text);
        write("quests", "b.yml", text);
        try (YamlConfigurations service = newService()) {
            ConfigScope main = scope(service, "quests");
            ConfigScope extra = scope(service, "quests", "extra");
            ManagedConfig<AwardsFile> inMain = awards(main, "a.yml", skipping);
            ManagedConfig<AwardsFile> inExtra = awards(extra, "b.yml", skipping);
            scope(service, "questitems").type(Award.class, ItemAward.class).name("item").into("quests/main").register();

            assertEquals(1, inMain.loadValue().awards.size());
            assertTrue(inExtra.loadValue().awards.isEmpty(), "quests/extra is not covered");
        }
    }

    @Test
    void aWordAddedToAPluginCoversEveryModuleOfIt() throws IOException {
        String text = "awards:\n  - type: item\n";
        write("quests", "a.yml", text);
        write("quests", "b.yml", text);
        try (YamlConfigurations service = newService()) {
            scope(service, "questitems").type(Award.class, ItemAward.class).name("item").into("quests").register();
            ManagedConfig<AwardsFile> inMain = awards(scope(service, "quests"), "a.yml");
            ManagedConfig<AwardsFile> inExtra = awards(scope(service, "quests", "extra"), "b.yml");

            assertEquals(1, inMain.loadValue().awards.size());
            assertEquals(1, inExtra.loadValue().awards.size());
        }
    }

    @Test
    void aWordCanBeAimedAtARegisteredFileObject() throws IOException {
        write("quests", "awards.yml", "awards:\n  - type: stack\n");
        try (YamlConfigurations service = newService()) {
            ManagedConfig<AwardsFile> awards = awards(scope(service, "quests"), "awards.yml");
            scope(service, "questitems").type(Award.class, ItemAward.class).name("item").alias("stack").into(awards).register();

            assertEquals(1, awards.loadValue().awards.size());
        }
    }

    @Test
    void aWordStopsWorkingAfterThePluginThatAddedItIsClosed() throws IOException {
        write("quests", "awards.yml", "awards:\n  - type: item\n");
        try (YamlConfigurations service = newService()) {
            ManagedConfig<AwardsFile> awards = awards(scope(service, "quests"), "awards.yml", skipping);
            ConfigScope items = scope(service, "questitems");
            items.type(Award.class, ItemAward.class).name("item").into("quests/main:awards.yml").register();
            assertEquals(1, awards.loadValue().awards.size());

            items.close();

            assertTrue(awards.reloadValue().awards.isEmpty());
        }
    }

    @Test
    void aFileOnlySeesWordsThatCoverIt() throws IOException {
        write("quests", "awards.yml", "awards:\n  - type: item\n");
        write("shop", "awards.yml", "awards:\n  - type: item\n");
        try (YamlConfigurations service = newService()) {
            scope(service, "questitems").type(Award.class, ItemAward.class).name("item").into("quests").register();
            ManagedConfig<AwardsFile> shop = awards(scope(service, "shop"), "awards.yml", skipping);

            assertTrue(shop.loadValue().awards.isEmpty());
        }
    }

    @Test
    void withoutIntoAWordWorksInTheModuleThatRegistersIt() throws IOException {
        write("quests", "awards.yml", "awards:\n  - type: item\n");
        try (YamlConfigurations service = newService()) {
            ConfigScope main = scope(service, "quests");
            main.type(Award.class, ItemAward.class).name("item").register();

            assertEquals(1, awards(main, "awards.yml").loadValue().awards.size());
        }
    }

    @Test
    void typesListsTheWordsOfABaseTypeAndWhoAddedThem() throws IOException {
        try (YamlConfigurations service = newService()) {
            scope(service, "questitems").type(Award.class, ItemAward.class).name("item").alias("stack").priority(4)
                    .into("quests").register();

            List<ConfigTypeInfo> words = service.types(Award.class);
            List<String> names = new ArrayList<>();
            for (ConfigTypeInfo word : words) {
                names.add(word.getName());
            }
            assertEquals(Arrays.asList("money", "item"), names);
            assertNull(words.get(0).getOwner());
            assertEquals("questitems", words.get(1).getOwner().getValue());
            assertEquals(new HashSet<>(Collections.singletonList("stack")), words.get(1).getAliases());
            assertEquals(4, words.get(1).getPriority());
            assertEquals("quests", words.get(1).getTarget().toString());
        }
    }

    // ── Protection ────────────────────────────────────────────────────────────────────────────

    @Test
    void aWordThatTheBaseTypeDeclaresCannotBeTaken() throws IOException {
        try (YamlConfigurations service = newService()) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> scope(service, "evil").type(Award.class, ItemAward.class).name("money").into("quests").register());
            assertTrue(String.valueOf(error.getMessage()).contains("money"), error.getMessage());
            assertThrows(IllegalArgumentException.class,
                    () -> scope(service, "evil").type(Award.class, ItemAward.class).name("fine").alias("MONEY").into("quests").register());
        }
    }

    @Test
    void aWordThatTheFilesOwnPluginPublishedCannotBeTakenByAnotherPlugin() throws IOException {
        try (YamlConfigurations service = newService()) {
            scope(service, "quests").type(Award.class, GoldAward.class).name("gold").register();

            assertThrows(IllegalArgumentException.class,
                    () -> scope(service, "questitems").type(Award.class, ItemAward.class).name("gold").into("quests").register());
            // A different word is fine.
            scope(service, "questitems").type(Award.class, ItemAward.class).name("item").into("quests").register();
        }
    }

    @Test
    void onePluginCannotPublishTheSameWordTwiceForOverlappingFiles() throws IOException {
        try (YamlConfigurations service = newService()) {
            ConfigScope items = scope(service, "questitems");
            items.type(Award.class, ItemAward.class).name("item").into("quests").register();

            assertThrows(IllegalArgumentException.class,
                    () -> items.type(Award.class, ItemAward.class).name("item").into("quests/main:awards.yml").register());
            // Files that no earlier registration covers are fine.
            items.type(Award.class, ItemAward.class).name("item").into("shop").register();
        }
    }

    @Test
    void twoPluginsMayAddOneWordAndAPriorityDecides() throws IOException {
        write("quests", "awards.yml", "awards:\n  - type: thing\n");
        try (YamlConfigurations service = newService()) {
            ManagedConfig<AwardsFile> awards = awards(scope(service, "quests"), "awards.yml");
            scope(service, "one").type(Award.class, ItemAward.class).name("thing").priority(1).into("quests").register();
            scope(service, "two").type(Award.class, GoldAward.class).name("thing").priority(9).into("quests").register();

            assertTrue(awards.loadValue().awards.get(0) instanceof GoldAward);
        }
    }

    // ── Roots and read-only files ─────────────────────────────────────────────────────────────

    @Test
    void readOnlyReadsAFileAsItIsAndNeverWritesIt() throws IOException {
        String text = "awards:\n  - type: money\n    amount: 9\n# keep me\n";
        write("quests", "awards.yml", text);
        try (YamlConfigurations service = newService()) {
            ConfigOptions options = ConfigOptions.builder().readOnly().build();
            ManagedConfig<AwardsFile> awards = awards(scope(service, "quests"), "awards.yml", options);

            assertEquals(new MoneyAward(9), awards.loadValue().awards.get(0));
            assertEquals(text, read(plugins().resolve("quests").resolve("awards.yml")));
            assertThrows(IllegalStateException.class, awards::save);
            assertTrue(awards.getInfo().getReadOnly());
        }
    }

    @Test
    void readOnlyDoesNotCreateAMissingFile() throws IOException {
        try (YamlConfigurations service = newService()) {
            ManagedConfig<AwardsFile> awards = awards(scope(service, "quests"), "awards.yml", ConfigOptions.builder().readOnly().build());

            assertThrows(IllegalStateException.class, awards::load);
            assertFalse(Files.exists(plugins().resolve("quests").resolve("awards.yml")));
        }
    }

    @Test
    void twoPluginsMayReadOneFileButOnlyOneMayWriteIt() throws IOException {
        write("shop", "prices.yml", "awards: []\n");
        try (YamlConfigurations service = newService()) {
            ConfigOptions readOnly = ConfigOptions.builder().readOnly().root(ConfigRoot.PLUGINS).build();
            ConfigOptions writing = ConfigOptions.builder().root(ConfigRoot.PLUGINS).build();
            awards(scope(service, "quests"), "shop/prices.yml", readOnly);
            awards(scope(service, "quests", "extra"), "shop/prices.yml", readOnly);
            awards(scope(service, "other"), "shop/prices.yml", writing);

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> awards(scope(service, "third"), "shop/prices.yml", writing));
            assertTrue(String.valueOf(error.getMessage()).contains("already managed"), error.getMessage());
        }
    }

    @Test
    void theSameFileCannotBeRegisteredTwiceByOneModule() throws IOException {
        try (YamlConfigurations service = newService()) {
            ConfigScope quests = scope(service, "quests");
            awards(quests, "awards.yml");

            assertThrows(IllegalStateException.class, () -> awards(quests, "awards.yml"));
        }
    }

    @Test
    void aFileInThePluginsFolderGetsTheRootInItsIdentifierAndIsNeverCreated() throws IOException {
        try (YamlConfigurations service = newService()) {
            ManagedConfig<AwardsFile> config = awards(scope(service, "quests"), "essentials/config.yml",
                    ConfigOptions.builder().root(ConfigRoot.PLUGINS).build());

            assertEquals("quests/main:@plugins/essentials/config.yml", config.getId().toString());
            assertSame(config, service.get("quests/main:@plugins/essentials/config.yml"));
            assertSame(config, scope(service, "quests").get("@plugins/essentials/config.yml"));
            assertThrows(IllegalStateException.class, config::load);
            assertFalse(Files.exists(plugins().resolve("essentials")), "somebody else's folder stays untouched");
        }
    }

    @Test
    void aFileInThePluginsFolderIsReadAndBackedUpBeforeARewrite() throws IOException {
        write("essentials", "config.yml", "awards:\n  - type: money\n    amount: 3\n");
        try (YamlConfigurations service = newService()) {
            ManagedConfig<AwardsFile> config = awards(scope(service, "quests"), "essentials/config.yml",
                    ConfigOptions.builder().root(ConfigRoot.PLUGINS).backups(false).build());

            assertEquals(new MoneyAward(3), config.loadValue().awards.get(0));
            List<String> backups = new ArrayList<>();
            try (Stream<Path> files = Files.list(plugins().resolve("essentials"))) {
                files.forEach(file -> backups.add(file.getFileName().toString()));
            }
            boolean any = false;
            for (String name : backups) {
                any |= name.contains(".bak");
            }
            assertTrue(any, "a backup was made although the options turned backups off: " + backups);
        }
    }

    @Test
    void aFileInTheServerRootIsFoundNextToThePluginsFolder() throws IOException {
        Files.write(server.resolve("bukkit.yml"), "awards: []\n".getBytes(StandardCharsets.UTF_8));
        try (YamlConfigurations service = newService()) {
            ManagedConfig<AwardsFile> config = awards(scope(service, "quests"), "bukkit.yml",
                    ConfigOptions.builder().root(ConfigRoot.SERVER).readOnly().build());

            assertTrue(config.loadValue().awards.isEmpty());
            assertEquals("quests/main:@server/bukkit.yml", config.getId().toString());
        }
    }

    @Test
    void aPathCannotLeaveItsRoot() throws IOException {
        try (YamlConfigurations service = newService()) {
            ConfigScope quests = scope(service, "quests");
            assertThrows(IllegalArgumentException.class, () -> awards(quests, "../shop/prices.yml"));
            assertThrows(IllegalArgumentException.class, () -> awards(quests, "/etc/x.yml"));
            assertThrows(IllegalArgumentException.class,
                    () -> awards(quests, "../../outside.yml", ConfigOptions.builder().root(ConfigRoot.PLUGINS).build()));
            assertThrows(IllegalArgumentException.class, () -> awards(quests, "notes.txt"));
        }
    }

    @Test
    void filesOfOtherPluginsAreNotAnnouncedToDiagnostics() throws IOException {
        write("essentials", "config.yml", "awards: []\n");
        try (YamlConfigurations service = newService()) {
            List<String> announced = new ArrayList<>();
            service.setFileListener(file -> {
                announced.add(file.getPath());
                return null;
            });
            ConfigScope quests = scope(service, "quests");
            awards(quests, "own.yml");
            awards(quests, "essentials/config.yml", ConfigOptions.builder().root(ConfigRoot.PLUGINS).readOnly().build());

            assertEquals(Collections.singletonList("own.yml"), announced);
        }
    }

    @Test
    void aPluginOpensAFileAtAnyAbsolutePathAndTheFileIsCreatedAndListed(@TempDir Path elsewhere) throws IOException {
        try (YamlConfigurations service = newService()) {
            Path file = elsewhere.resolve("deep/folder/awards.yml");
            ManagedConfig<AwardsFile> config = scope(service, "quests").yamlAt(file, AwardsFile.class, AwardsFile::new);

            config.load();

            assertTrue(Files.exists(file), "created where the plugin asked");
            ConfigId id = config.getId();
            assertEquals(ConfigRoot.EXTERNAL, id.getRoot());
            assertEquals(id, ConfigId.parse(id.toString()), id.toString());
            assertEquals(file.toAbsolutePath().normalize(), service.get(id).getInfo().getFile());
        }
    }

    @Test
    void anExternalPathMustBeAbsoluteAndASecondWriterOfTheSameFileIsRefused(@TempDir Path elsewhere) throws IOException {
        try (YamlConfigurations service = newService()) {
            ConfigScope quests = scope(service, "quests");
            assertThrows(IllegalArgumentException.class,
                    () -> awards(quests, "relative.yml", ConfigOptions.builder().root(ConfigRoot.EXTERNAL).build()));
            Path file = elsewhere.resolve("shared.yml");
            quests.yamlAt(file, AwardsFile.class, AwardsFile::new);
            assertThrows(IllegalStateException.class,
                    () -> scope(service, "other").yamlAt(file, AwardsFile.class, AwardsFile::new));
        }
    }
}
