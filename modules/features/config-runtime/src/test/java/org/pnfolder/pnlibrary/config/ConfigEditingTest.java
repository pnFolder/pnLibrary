package org.pnfolder.pnlibrary.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.config.ConfigApply;
import org.pnfolder.pnlibrary.api.config.ConfigChange;
import org.pnfolder.pnlibrary.api.config.ConfigChangeCause;
import org.pnfolder.pnlibrary.api.config.ConfigEditException;
import org.pnfolder.pnlibrary.api.config.ConfigEditResult;
import org.pnfolder.pnlibrary.api.config.ConfigScope;
import org.pnfolder.pnlibrary.api.config.ConfigSetting;
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind;
import org.pnfolder.pnlibrary.api.config.ManagedConfig;
import org.pnfolder.pnlibrary.api.config.ReloadRequest;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

class ConfigEditingTest {
    @TempDir
    Path server;

    private ManagedConfig<EditableFile> open(YamlConfigurations service) throws IOException {
        Path folder = server.resolve("plugins/quests");
        Files.createDirectories(folder);
        ConfigScope scope = service.scope(new FakeConfigOwner(folder.toFile()), PluginId.of("quests-main"),
                PluginId.of("quests"), ModuleId.of("main"));
        ManagedConfig<EditableFile> config = scope.yaml("settings.yml", EditableFile.class, EditableFile::new);
        config.load();
        return config;
    }

    private String read() throws IOException {
        return new String(Files.readAllBytes(server.resolve("plugins/quests/settings.yml")), StandardCharsets.UTF_8);
    }

    private static ConfigSetting setting(ManagedConfig<?> config, String path) {
        for (ConfigSetting setting : config.getSettings()) {
            if (setting.getPath().equals(path)) {
                return setting;
            }
        }
        throw new AssertionError("no setting " + path);
    }

    @Test
    void onlyMarkedFieldsAreOfferedAndSecretsNever() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            ManagedConfig<EditableFile> config = open(service);

            Set<String> paths = new HashSet<>();
            for (ConfigSetting setting : config.getSettings()) {
                paths.add(setting.getPath());
            }

            assertEquals(new HashSet<>(Arrays.asList("greeting", "poolSize", "difficulty", "shop.enabled", "shop.tax")), paths);
            assertFalse(paths.contains("token"));
            assertFalse(paths.contains("hidden"));
            ConfigSetting pool = setting(config, "poolSize");
            assertEquals(ConfigSettingKind.INTEGER, pool.getKind());
            assertEquals(Double.valueOf(1.0), pool.getMin());
            assertEquals(Double.valueOf(64.0), pool.getMax());
            assertEquals("10", pool.getValue());
            assertEquals(Arrays.asList("EASY", "HARD"), setting(config, "difficulty").getChoices());
        }
    }

    @Test
    void anEditChangesTheValueInMemoryAndInTheFile() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            ManagedConfig<EditableFile> config = open(service);

            ConfigEditResult result = config.edit("greeting", "Здравствуй");
            config.edit("shop.enabled", "off");
            config.edit("difficulty", "hard");

            assertEquals("Привет", result.getPrevious());
            assertEquals("Здравствуй", result.getSetting().getValue());
            assertEquals("Здравствуй", config.get().greeting);
            assertFalse(config.get().shop.enabled);
            assertEquals(Difficulty.HARD, config.get().difficulty);
            String text = read();
            assertTrue(text.contains("Здравствуй"), text);
            assertTrue(text.contains("HARD"), text);
        }
    }

    @Test
    void aWrongValueIsRefusedAndNothingChanges() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            ManagedConfig<EditableFile> config = open(service);
            String before = read();

            assertThrows(ConfigEditException.class, () -> config.edit("poolSize", "many"));
            assertThrows(ConfigEditException.class, () -> config.edit("poolSize", "1000"));
            assertThrows(ConfigEditException.class, () -> config.edit("shop.tax", "5"));
            assertThrows(ConfigEditException.class, () -> config.edit("difficulty", "medium"));
            assertThrows(ConfigEditException.class, () -> config.edit("token", "x"));
            assertThrows(ConfigEditException.class, () -> config.edit("hidden", "x"));

            assertEquals(10, config.get().poolSize);
            assertEquals(0.1, config.get().shop.tax);
            assertEquals("s3cret", config.get().token);
            assertEquals(before, read());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void listenersHearAboutReadsEditsAndSettingsThatNeedAReload() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            ManagedConfig<EditableFile> config = open(service);
            List<ConfigChange> changes = new ArrayList<>();
            List<ReloadRequest> requests = new ArrayList<>();
            List<ReloadRequest> second = new ArrayList<>();
            config.onChanged((Consumer<ConfigChange>) changes::add);
            config.onReloadRequired((Consumer<ReloadRequest>) requests::add, (Consumer<ReloadRequest>) second::add);

            config.edit("greeting", "Hi");
            assertTrue(requests.isEmpty(), "a live setting asks for nothing");
            config.edit("poolSize", "20");
            config.edit("difficulty", "HARD");

            List<ConfigApply> applies = new ArrayList<>();
            for (ReloadRequest request : requests) {
                applies.add(request.getApply());
            }
            assertEquals(Arrays.asList(ConfigApply.RESTART, ConfigApply.RELOAD), applies);
            assertEquals(2, second.size(), "every listener is called");
            assertEquals(ConfigApply.RESTART, service.get(config.getId()).getInfo().getPendingApply());
            List<String> paths = new ArrayList<>();
            for (ConfigChange change : changes) {
                assertEquals(1, change.getPaths().size());
                paths.add(change.getPaths().get(0));
                assertEquals(ConfigChangeCause.EDITED, change.getCause());
            }
            assertEquals(Arrays.asList("greeting", "poolSize", "difficulty"), paths);

            config.reload();
            assertEquals(ConfigChangeCause.RELOADED, changes.get(changes.size() - 1).getCause());
            assertEquals(ConfigApply.RESTART, config.getInfo().getPendingApply(), "a reload does not make a restart");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPendingReloadIsClearedByReadingTheFileAgainAndAFailingListenerDoesNotStopOthers() throws Exception {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            ManagedConfig<EditableFile> config = open(service);
            List<ReloadRequest> heard = new ArrayList<>();
            AutoCloseable subscription = config.onReloadRequired(
                    (Consumer<ReloadRequest>) request -> {
                        throw new IllegalStateException("broken listener");
                    },
                    (Consumer<ReloadRequest>) heard::add);

            config.edit("difficulty", "HARD");
            assertEquals(1, heard.size());
            assertEquals(ConfigApply.RELOAD, config.getInfo().getPendingApply());

            config.reload();
            assertNull(config.getInfo().getPendingApply());

            subscription.close();
            config.edit("difficulty", "EASY");
            assertEquals(1, heard.size(), "a closed subscription hears nothing");
        }
    }
}
