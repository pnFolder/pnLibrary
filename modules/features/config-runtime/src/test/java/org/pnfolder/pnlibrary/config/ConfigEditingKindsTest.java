package org.pnfolder.pnlibrary.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.config.ConfigEditException;
import org.pnfolder.pnlibrary.api.config.ConfigScope;
import org.pnfolder.pnlibrary.api.config.ConfigSetting;
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind;
import org.pnfolder.pnlibrary.api.config.ManagedConfig;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

class ConfigEditingKindsTest {
    @TempDir
    Path server;

    private ManagedConfig<RichFile> open(YamlConfigurations service) throws IOException {
        Path folder = server.resolve("plugins/quests");
        Files.createDirectories(folder);
        ConfigScope scope = service.scope(new FakeConfigOwner(folder.toFile()), PluginId.of("quests-main"),
                PluginId.of("quests"), ModuleId.of("main"));
        ManagedConfig<RichFile> config = scope.yaml("rich.yml", RichFile.class, RichFile::new);
        config.load();
        return config;
    }

    @Test
    void aTimeIsTypedWithUnitsAndKeptInTheUnitOfTheField() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            ManagedConfig<RichFile> config = open(service);

            config.edit("delay", "1h30m");
            assertEquals(5400, config.get().delay);

            config.edit("delay", "2 мин");
            assertEquals(120, config.get().delay);

            config.edit("delay", "45");
            assertEquals(45, config.get().delay);
        }
    }

    @Test
    void theStepTheUnitAndTheHintsReachTheMenu() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            Map<String, ConfigSetting> settings = new LinkedHashMap<String, ConfigSetting>();
            for (ConfigSetting setting : open(service).getSettings()) {
                settings.put(setting.getPath(), setting);
            }

            assertEquals(TimeUnit.SECONDS, settings.get("delay").getDuration());
            assertEquals(5.0, settings.get("radius").getStep());
            assertEquals("блоков", settings.get("radius").getUnit());
            assertEquals("мир;x;y;z", settings.get("place").getFormat());
            assertEquals(Collections.singletonList("world;0;64;0"), settings.get("place").getExamples());
        }
    }

    @Test
    void aStrictListOfValuesMakesATextAChoiceThatRefusesAnythingElse() throws IOException {
        try (YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE)) {
            ManagedConfig<RichFile> config = open(service);
            ConfigSetting mode = null;
            for (ConfigSetting setting : config.getSettings()) {
                if (setting.getPath().equals("mode")) {
                    mode = setting;
                }
            }

            assertEquals(ConfigSettingKind.CHOICE, mode.getKind());
            assertEquals(Arrays.asList("alpha", "beta", "gamma"), mode.getChoices());

            config.edit("mode", "BETA");
            assertEquals("beta", config.get().mode);
            assertThrows(ConfigEditException.class, () -> config.edit("mode", "delta"));
        }
    }
}
