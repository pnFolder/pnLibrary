package org.pnfolder.pnlibrary.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.config.ConfigAddress;
import org.pnfolder.pnlibrary.api.config.ConfigId;
import org.pnfolder.pnlibrary.api.config.ConfigInfo;
import org.pnfolder.pnlibrary.api.config.ConfigRoot;
import org.pnfolder.pnlibrary.api.config.ConfigScope;
import org.pnfolder.pnlibrary.api.config.ConfigSerializationContext;
import org.pnfolder.pnlibrary.api.config.ConfigSerializer;
import org.pnfolder.pnlibrary.api.config.ConfigSerializerInfo;
import org.pnfolder.pnlibrary.api.config.ConfigSerializerRegistration;
import org.pnfolder.pnlibrary.api.config.ConfigTypeConflict;
import org.pnfolder.pnlibrary.api.config.ConfigTypeRegistration;
import org.pnfolder.pnlibrary.api.config.ManagedConfig;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/** Serializers and words that one plugin adds to other plugins' files: to all of them, one plugin, one module or one file. */
class ConfigAdditionsTest {
    @TempDir
    Path server;

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

    private String text(String name) throws IOException {
        return new String(Files.readAllBytes(plugins().resolve("shop/" + name)), StandardCharsets.UTF_8);
    }

    private static Place spawnOf(ConfigScope scope, String file) {
        return scope.yaml(file, PlaceFile.class, PlaceFile::new).load().getValue().spawn;
    }

    private static Place spawnOf(ConfigScope scope) {
        return spawnOf(scope, "places.yml");
    }

    private static Place spawnOrNull(ConfigScope scope, String file) {
        try {
            return spawnOf(scope, file);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static Place spawnOrNull(ConfigScope scope) {
        return spawnOrNull(scope, "places.yml");
    }

    @Test
    void theAddressStarCoversEveryFile() {
        ConfigAddress all = ConfigAddress.parse("*");

        assertEquals(ConfigAddress.Level.ALL, all.getLevel());
        assertTrue(all.isAll());
        assertEquals("*", all.toString());
        assertTrue(all.matches(ConfigId.parse("quests/main:a.yml")));
        assertTrue(all.matches(ConfigId.parse("shop/extra:b.yml")));
        assertTrue(all.overlaps(ConfigAddress.parse("shop")));
        assertNotEquals(all, ConfigAddress.parse("shop"));
        assertEquals(ConfigAddress.everywhere(), all);
        // A plugin named "all" is an ordinary plugin and is not mixed up with everything
        assertNotEquals(all, ConfigAddress.plugin("all"));
        assertFalse(ConfigAddress.plugin("all").isAll());
        assertThrows(IllegalStateException.class, all::getPlugin);
        assertThrows(IllegalArgumentException.class, () -> ConfigId.parse("*"));
    }

    @Test
    void aWrittenFileShowsWhetherTheSerializerReachesItAndARegistrationCanBeSwitched() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            ConfigScope scope = scope(service, "shop");
            ManagedConfig<PlaceFile> first = scope.yaml("a.yml", PlaceFile.class, PlaceFile::new);
            first.load();
            ManagedConfig<PlaceFile> second = scope.yaml("b.yml", PlaceFile.class, PlaceFile::new);
            second.load();

            try (ConfigSerializerRegistration registration = scope.serializerFor(Place.class, new PlaceSerializer()).into(first).register()) {
                first.resetToDefaults();
                second.resetToDefaults();
                assertTrue(text("a.yml").contains("spawn: lobby,1"), text("a.yml"));
                assertFalse(text("b.yml").contains("spawn: lobby,1"), text("b.yml"));
            }

            // Closed: the file is written the old way again
            first.resetToDefaults();
            assertFalse(text("a.yml").contains("spawn: lobby,1"), text("a.yml"));

            scope.serializerFor(Place.class, new PlaceSerializer()).into(scope).register();
            second.resetToDefaults();
            assertTrue(text("b.yml").contains("spawn: lobby,1"), text("b.yml"));
        }
    }

    @Test
    void anAddressIsBuiltFromPluginModuleAndFileWithoutWritingAString() {
        ConfigAddress module = ConfigAddress.plugin("shop").inModule("main");
        ConfigAddress file = module.inFile("rewards.yml");

        assertEquals("shop/main:rewards.yml", file.toString());
        assertEquals(ConfigAddress.parse("shop/main"), module);
        assertEquals(ConfigAddress.parse("shop/main:@server/bukkit.yml"), module.inFile("bukkit.yml", ConfigRoot.SERVER));
        assertEquals(ConfigAddress.Level.FILE, file.getLevel());
        assertThrows(IllegalArgumentException.class, () -> ConfigAddress.plugin("shop").inFile("a.yml"));
        assertThrows(IllegalArgumentException.class, () -> file.inModule("other"));
    }

    @Test
    void intoTakesAPluginAScopeOrACatalogEntryAsTheyAre() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            write("shop", "places.yml", "spawn: arena,7\n");
            write("other", "places.yml", "spawn: hub,3\n");
            write("third", "places.yml", "spawn: end,9\n");
            ConfigScope shop = scope(service, "shop");
            ConfigScope other = scope(service, "other");
            ConfigScope third = scope(service, "third");
            ManagedConfig<PlaceFile> thirdFile = third.yaml("places.yml", PlaceFile.class, PlaceFile::new);
            ConfigScope publisher = scope(service, "quests");

            publisher.serializerFor(Place.class, new PlaceSerializer()).into(PluginId.of("shop")).register();
            publisher.serializerFor(Place.class, new PlaceSerializer()).into(other).register();
            ConfigInfo thirdInfo = null;
            for (ConfigInfo info : service.all()) {
                if (info.getPlugin().getValue().equals("third")) {
                    thirdInfo = info;
                }
            }
            publisher.serializerFor(Place.class, new PlaceSerializer()).into(thirdInfo).register();

            assertEquals(new Place("arena", 7), spawnOf(shop));
            assertEquals(new Place("hub", 3), spawnOf(other));
            assertEquals(new Place("end", 9), thirdFile.reload().getValue().spawn);
        }
    }

    @Test
    void withoutAnAddressASerializerWorksOnlyInItsOwnModule() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            write("shop", "places.yml", "spawn: arena,7\n");
            scope(service, "quests").serializerFor(Place.class, new PlaceSerializer()).register();

            // Nobody told shop how to read a place, so it is read as a section and the line does not fit
            assertNotEquals(new Place("arena", 7), spawnOrNull(scope(service, "shop")));
        }
    }

    @Test
    void aSerializerAddedToAPluginWorksInItsFiles() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            write("shop", "places.yml", "spawn: arena,7\n");
            ConfigScope shop = scope(service, "shop");
            ConfigScope other = scope(service, "other");
            scope(service, "quests").serializerFor(Place.class, new PlaceSerializer()).into("shop").register();
            write("other", "places.yml", "spawn: arena,7\n");

            assertEquals(new Place("arena", 7), spawnOf(shop));
            assertNotEquals(new Place("arena", 7), spawnOrNull(other), "another plugin is not covered");
        }
    }

    @Test
    void aSerializerAddedToEverythingWorksInEveryPlugin() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            write("shop", "places.yml", "spawn: arena,7\n");
            write("other", "places.yml", "spawn: hub,3\n");
            scope(service, "quests").serializerFor(Place.class, new PlaceSerializer()).everywhere().register();

            assertEquals(new Place("arena", 7), spawnOf(scope(service, "shop")));
            assertEquals(new Place("hub", 3), spawnOf(scope(service, "other")));
            List<String> owners = new java.util.ArrayList<String>();
            for (ConfigSerializerInfo info : service.serializers()) {
                owners.add(info.getOwner().getValue());
            }
            assertEquals(Collections.singletonList("quests"), owners);
        }
    }

    @Test
    void aSerializerCanBeLimitedToAModuleOrOneFile() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            write("shop", "one.yml", "spawn: a,1\n");
            write("shop", "two.yml", "spawn: b,2\n");
            write("shop", "three.yml", "spawn: c,3\n");
            ConfigScope main = scope(service, "shop");
            ConfigScope extra = scope(service, "shop", "extra");
            ConfigScope publisher = scope(service, "quests");
            publisher.serializerFor(Place.class, new PlaceSerializer()).into("shop/main:one.yml").register();

            assertEquals(new Place("a", 1), spawnOf(main, "one.yml"));
            assertNotEquals(new Place("b", 2), spawnOrNull(main, "two.yml"), "another file of the module is not covered");

            publisher.serializerFor(Place.class, new PlaceSerializer("x:")).into("shop/extra").register();
            write("shop", "three.yml", "spawn: x:c,3\n");
            assertEquals(new Place("c", 3), spawnOf(extra, "three.yml"));
        }
    }

    @Test
    void theModulesOwnSerializerIsNotReplacedAndTheHigherPriorityWinsAmongAdditions() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            write("shop", "places.yml", "spawn: own:arena,7\n");
            ConfigScope shop = scope(service, "shop").serializer(Place.class, new PlaceSerializer("own:"));
            scope(service, "quests").serializerFor(Place.class, new PlaceSerializer("low:")).priority(1).everywhere().register();

            assertEquals(new Place("arena", 7), spawnOf(shop));

            write("other", "places.yml", "spawn: high:hub,3\n");
            scope(service, "pnmaps").serializerFor(Place.class, new PlaceSerializer("high:")).priority(9).everywhere().register();
            assertEquals(new Place("hub", 3), spawnOf(scope(service, "other")));
        }
    }

    @Test
    void ordinaryTypesCannotBeTakenOverForOtherPlugins() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            ConfigScope publisher = scope(service, "quests");
            ConfigSerializer<String> text = new ConfigSerializer<String>() {
                @Override
                @NotNull
                public Object serialize(@NotNull String value, @NotNull ConfigSerializationContext context) {
                    return new StringBuilder(value).reverse().toString();
                }

                @Override
                @NotNull
                public String deserialize(@Nullable Object value, @NotNull ConfigSerializationContext context) {
                    return new StringBuilder(String.valueOf(value)).reverse().toString();
                }
            };

            assertThrows(IllegalArgumentException.class, () -> publisher.serializerFor(String.class, text).everywhere().register());
            assertThrows(IllegalArgumentException.class, () -> publisher.serializerFor(String.class, text).into("shop").register());
            // Its own module may do what it likes
            publisher.serializerFor(String.class, text).register();
        }
    }

    @Test
    void closingTheRegistrationOrThePublisherTakesTheSerializerBack() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            write("shop", "places.yml", "spawn: arena,7\n");
            ConfigScope publisher = scope(service, "quests");
            ConfigSerializerRegistration registration = publisher.serializerFor(Place.class, new PlaceSerializer()).into("shop").register();

            assertEquals(1, service.serializers().size());
            assertThrows(IllegalStateException.class,
                    () -> publisher.serializerFor(Place.class, new PlaceSerializer()).into("shop/main").register());
            registration.close();
            assertTrue(service.serializers().isEmpty());
            assertFalse(registration.isActive());

            publisher.serializerFor(Place.class, new PlaceSerializer()).into("shop").register();
            publisher.close();
            assertTrue(service.serializers().isEmpty());
        }
    }

    @Test
    void aWordAddedForEverythingWorksInEveryPluginButNeverReplacesAWordOfTheFilesPlugin() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            write("quests", "awards.yml", "awards:\n  - type: item\n    material: GOLD\n");
            write("shop", "awards.yml", "awards:\n  - type: item\n    material: GOLD\n");
            ConfigScope quests = scope(service, "quests");
            ConfigScope shop = scope(service, "shop");
            // quests has its own meaning of the word item
            quests.type(Award.class, GoldAward.class).name("item").register();
            scope(service, "questitems").type(Award.class, ItemAward.class).name("item").everywhere().register();

            Award own = quests.yaml("awards.yml", AwardsFile.class, AwardsFile::new).load().getValue().awards.get(0);
            Award added = shop.yaml("awards.yml", AwardsFile.class, AwardsFile::new).load().getValue().awards.get(0);

            assertTrue(own instanceof GoldAward, "the owner's word wins in its own file: " + own);
            assertEquals(new ItemAward("GOLD", 1), added);
        }
    }

    @SuppressWarnings("unchecked")
    private static Award first(ConfigScope scope) {
        ManagedConfig<?> existing = scope.get("awards.yml");
        ManagedConfig<?> file = existing != null ? existing : scope.yaml("awards.yml", AwardsFile.class, AwardsFile::new);
        return ((ManagedConfig<AwardsFile>) file).reload().getValue().awards.get(0);
    }

    private static Award firstOrNull(ConfigScope scope) {
        try {
            return first(scope);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static String pick() {
        return "awards:\n  - type: pick\n    material: GOLD\n";
    }

    @Test
    void aTakenWordFailsByDefaultIsKeptOnRequestAndIsReplacedOnRequest() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            write("quests", "awards.yml", pick());
            ConfigScope quests = scope(service, "quests");
            ConfigScope library = scope(service, "lib");
            library.type(Award.class, ItemAward.class).name("pick").everywhere().register();

            assertThrows(IllegalArgumentException.class,
                    () -> library.type(Award.class, GoldAward.class).name("pick").everywhere().register());
            ConfigTypeRegistration kept = library.type(Award.class, GoldAward.class).name("pick").everywhere()
                    .onConflict(ConfigTypeConflict.KEEP).register();
            assertFalse(kept.isActive());
            assertTrue(first(quests) instanceof ItemAward);

            ConfigTypeRegistration replaced = library.type(Award.class, GoldAward.class).name("pick").everywhere()
                    .onConflict(ConfigTypeConflict.REPLACE).register();
            assertTrue(replaced.isActive());
            assertTrue(first(quests) instanceof GoldAward);
        }
    }

    @Test
    void oneWordCanBePublishedForSeveralPlacesAndWithdrawnTogether() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            write("quests", "awards.yml", pick());
            write("shop", "awards.yml", pick());
            write("farm", "awards.yml", pick());
            ConfigScope publisher = scope(service, "lib");

            List<ConfigTypeRegistration> handles = publisher.type(Award.class, ItemAward.class).name("pick")
                    .registerEach(Arrays.asList(ConfigAddress.plugin("quests"), ConfigAddress.plugin("shop")));

            assertEquals(2, handles.size());
            assertTrue(first(scope(service, "quests")) instanceof ItemAward);
            assertTrue(first(scope(service, "shop")) instanceof ItemAward);
            assertFalse(firstOrNull(scope(service, "farm")) instanceof ItemAward);
            for (ConfigTypeRegistration handle : handles) {
                handle.close();
            }
            assertFalse(firstOrNull(scope(service, "quests")) instanceof ItemAward);
        }
    }
}
