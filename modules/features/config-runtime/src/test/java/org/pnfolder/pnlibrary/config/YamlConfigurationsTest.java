package org.pnfolder.pnlibrary.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.config.ConfigOptions;
import org.pnfolder.pnlibrary.api.config.ConfigProblem;
import org.pnfolder.pnlibrary.api.config.ConfigScope;
import org.pnfolder.pnlibrary.api.config.ConfigTypeRegistration;
import org.pnfolder.pnlibrary.api.config.ConfigValidationException;
import org.pnfolder.pnlibrary.api.config.ConfigValidatorBuilder;
import org.pnfolder.pnlibrary.api.config.ConfigValueValidator;
import org.pnfolder.pnlibrary.api.config.ManagedConfig;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

class YamlConfigurationsTest {
    @TempDir
    Path directory;

    private YamlConfigurations service() {
        return new YamlConfigurations(FakeOwnerPlatform.INSTANCE);
    }

    private ConfigScope scope(YamlConfigurations service) {
        return service.scope(new FakeConfigOwner(directory.toFile()), PluginId.of("tester"));
    }

    private void write(String name, String text) throws IOException {
        Files.write(directory.resolve(name), text.getBytes(StandardCharsets.UTF_8));
    }

    private String read(String name) throws IOException {
        return new String(Files.readAllBytes(directory.resolve(name)), StandardCharsets.UTF_8);
    }

    private static ConfigValueValidator<ServiceSettings> notBlankName() {
        return new ConfigValidatorBuilder<ServiceSettings>()
                .require("name", "must not be blank", settings -> !settings.name.trim().isEmpty())
                .build();
    }

    @Test
    void createsTheFileFromDefaultsAndLoadsTheValue() {
        try (YamlConfigurations service = service()) {
            ManagedConfig<ServiceSettings> config = scope(service).yaml("settings.yml", ServiceSettings.class, ServiceSettings::new);
            ServiceSettings loaded = config.load().getValue();
            assertEquals(3, loaded.retries);
            assertTrue(Files.exists(directory.resolve("settings.yml")));
        }
    }

    @Test
    void loadAllLoadsTheHealthyFilesEvenWhenOneFileIsBroken() throws IOException {
        write("broken.yml", "retries: [unclosed");
        try (YamlConfigurations service = service()) {
            ConfigScope scope = scope(service);
            ManagedConfig<ServiceSettings> broken = scope.yaml("broken.yml", ServiceSettings.class, ServiceSettings::new);
            ManagedConfig<ServiceSettings> healthy = scope.yaml("healthy.yml", ServiceSettings.class, ServiceSettings::new);

            assertThrows(RuntimeException.class, scope::loadAll);

            assertTrue(healthy.isLoaded());
            assertTrue(!broken.isLoaded());
        }
    }

    @Test
    void annotationAndBuilderValidatorsReportTogether() throws IOException {
        write("settings.yml", "retries: 99\nname: \"\"\n");
        try (YamlConfigurations service = service()) {
            ManagedConfig<ServiceSettings> config = scope(service).yaml("settings.yml", ServiceSettings.class,
                    ServiceSettings::new, ConfigOptions.DEFAULT, notBlankName());
            ConfigValidationException error = assertThrows(ConfigValidationException.class, config::load);
            List<String> paths = new ArrayList<String>();
            for (ConfigProblem problem : error.getProblems()) {
                paths.add(problem.getPath());
            }
            assertTrue(paths.contains("retries"), "annotation problem expected, was " + paths);
            assertTrue(paths.contains("name"), "builder problem expected, was " + paths);
        }
    }

    @Test
    void builderValidatorPassesWhenTheValueIsValid() {
        try (YamlConfigurations service = service()) {
            ManagedConfig<ServiceSettings> config = scope(service).yaml("settings.yml", ServiceSettings.class,
                    ServiceSettings::new, ConfigOptions.DEFAULT, notBlankName());
            assertEquals("demo", config.load().getValue().name);
        }
    }

    @Test
    void everyNewFileCarriesAVersionMarkerWithABilingualWarning() throws IOException {
        try (YamlConfigurations service = service()) {
            scope(service).yaml("settings.yml", ServiceSettings.class, ServiceSettings::new).load();
            String text = read("settings.yml");
            assertTrue(text.contains("_config-version: 1"), text);
            assertTrue(text.contains("DO NOT EDIT OR REMOVE"), text);
            assertTrue(text.contains("НЕ МЕНЯЙТЕ И НЕ УДАЛЯЙТЕ"), text);
        }
    }

    @Test
    void newAndRewrittenFilesNeverCarryASupportNote() throws IOException {
        write("old.yml", "retries: 4\nname: custom\n");
        try (YamlConfigurations service = service()) {
            ConfigScope scope = scope(service);
            ManagedConfig<ServiceSettings> fresh = scope.yaml("settings.yml", ServiceSettings.class, ServiceSettings::new);
            fresh.load();
            fresh.save();
            ManagedConfig<ServiceSettings> old = scope.yaml("old.yml", ServiceSettings.class, ServiceSettings::new);
            old.load();
            old.reload();
            for (String name : Arrays.asList("settings.yml", "old.yml")) {
                String text = read(name);
                assertFalse(text.contains("discord.gg"), name + ": " + text);
                assertFalse(text.contains("write to us"), text);
                assertFalse(text.contains("пишите нам"), text);
            }
        }
    }

    @Test
    void anOldSupportNoteIsRemovedFromAnExistingFileAndNothingElseChanges() throws IOException {
        String note = "# Если у вас возникли проблемы с плагином, пишите нам: мы всегда рады помочь.\n"
                + "# If you have any problem with a plugin, write to us: we are always happy to help.\n"
                + "# Discord: https://discord.gg/old\n";
        write("old.yml", "_config-version: 1\n" + note + "# my note\nretries: 4\nname: custom\n");
        try (YamlConfigurations service = service()) {
            scope(service).yaml("old.yml", ServiceSettings.class, ServiceSettings::new).load();
            String text = read("old.yml");
            assertFalse(text.contains("discord.gg"), text);
            assertFalse(text.contains("If you have any problem"), text);
            assertTrue(text.contains("# my note\nretries: 4"), text);
            assertTrue(text.contains("name: custom"), text);
        }
    }

    @Test
    void administratorCommentsSurviveReloadAndAMissingMarkerIsAdded() throws IOException {
        write("settings.yml", "# my note\nretries: 4\nname: custom\n");
        try (YamlConfigurations service = service()) {
            ManagedConfig<ServiceSettings> config = scope(service).yaml("settings.yml", ServiceSettings.class, ServiceSettings::new);
            config.load();
            config.reload();
            String text = read("settings.yml");
            assertTrue(text.contains("# my note"), text);
            assertTrue(text.contains("_config-version: 1"), text);
            assertEquals(4, config.getValue().retries);
        }
    }

    @Test
    void withoutVersionOmitsTheMarker() throws IOException {
        try (YamlConfigurations service = service()) {
            ConfigOptions options = ConfigOptions.builder().withoutVersion().build();
            scope(service).yaml("settings.yml", ServiceSettings.class, ServiceSettings::new, options).load();
            assertFalse(read("settings.yml").contains("_config-version"));
        }
    }

    @Test
    void typeBuilderPublishesANamedImplementationAndClosesIt() {
        try (YamlConfigurations service = service()) {
            ConfigTypeRegistration registration = scope(service).type(Shape.class, Circle.class)
                    .name("Circle").alias("round").priority(5).register();
            assertEquals("circle", registration.getName());
            assertEquals(PluginId.of("tester"), registration.getOwner());
            assertEquals(Circle.class, registration.getImplementation());
            assertTrue(registration.isActive());
            registration.close();
            assertFalse(registration.isActive());
        }
    }

    @Test
    void typeBuilderRequiresANameBeforeRegistering() {
        try (YamlConfigurations service = service()) {
            assertThrows(IllegalArgumentException.class, () -> scope(service).type(Shape.class, Circle.class).register());
        }
    }
}
