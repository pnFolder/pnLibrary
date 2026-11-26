package org.pnfolder.pnlibrary.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.config.ConfigDocument;
import org.pnfolder.pnlibrary.api.config.ConfigOptions;
import org.pnfolder.pnlibrary.api.config.ConfigScope;
import org.pnfolder.pnlibrary.api.config.ConfigSteps;
import org.pnfolder.pnlibrary.api.config.ManagedConfig;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

class ConfigStepsTest {
    @TempDir
    Path directory;

    private ManagedConfig<StepSettings> open(ConfigSteps<?> steps) {
        YamlConfigurations service = new YamlConfigurations(FakeOwnerPlatform.INSTANCE);
        ConfigScope scope = service.scope(new FakeConfigOwner(directory.toFile()), PluginId.of("steps"));
        ConfigOptions options = ConfigOptions.builder().migrations(steps).build();
        return scope.yaml("settings.yml", StepSettings.class, StepSettings::new, options);
    }

    private void write(String text) throws IOException {
        Files.write(directory.resolve("settings.yml"), text.getBytes(StandardCharsets.UTF_8));
    }

    private String read() throws IOException {
        return new String(Files.readAllBytes(directory.resolve("settings.yml")), StandardCharsets.UTF_8);
    }

    @Test
    void aFileWithoutAMarkerRunsEveryVersionAboveOne() throws IOException {
        write("pool-size: 20\naddress: \"db.local:3306\"\nfirst: x\nsecond: y\n");

        StepSettings loaded = open(new StepsForTest()).load().getValue();

        assertEquals("db.local", loaded.host);
        assertEquals(3306, loaded.port);
        assertEquals(20, loaded.pool);
        assertEquals("y", loaded.first);
        assertEquals("x", loaded.second);
        assertTrue(read().contains("_config-version: 4"), read());
        assertFalse(read().contains("pool-size"));
        assertFalse(read().contains("address"));
    }

    @Test
    void reloadingAFileAtTheLatestVersionChangesNothing() throws IOException {
        write("_config-version: 4\nhost: h\nport: 1\npool: 5\nfirst: a\nsecond: b\n");
        ManagedConfig<StepSettings> config = open(new StepsForTest());

        config.load();
        String afterFirstLoad = read();
        config.reload();

        assertEquals(afterFirstLoad, read());
        assertEquals("h", config.getValue().host);
    }

    @Test
    void aSwapOfTwoValuesStoresEachOneUnderTheOtherKey() throws IOException {
        write("_config-version: 3\nhost: h\nport: 1\npool: 5\nfirst: left\nsecond: right\n");

        StepSettings loaded = open(new StepsForTest()).load().getValue();

        assertEquals("right", loaded.first);
        assertEquals("left", loaded.second);
    }

    @Test
    void aFileFromANewerVersionIsReadButNeverRewritten() throws IOException {
        String text = "_config-version: 9\nhost: future\nport: 2\npool: 3\nfirst: a\nsecond: b\n";
        write(text);

        StepSettings loaded = open(new StepsForTest()).load().getValue();

        assertEquals("future", loaded.host);
        assertEquals(text, read());
    }

    @Test
    void aFailingStepLeavesTheOriginalFileUntouched() throws IOException {
        String text = "address: \"no-port-here\"\nfirst: a\nsecond: b\n";
        write(text);

        assertThrows(IllegalStateException.class, () -> open(new StepsForTest()).load());
        assertEquals(text, read());
    }

    private static final class Gapped extends ConfigSteps<StepSettings> {
        Gapped() {
            version(2, changes -> changes.rename("pool-size", "pool"));
            version(4, changes -> changes.remove("second"));
        }
    }

    @Test
    void versionNumbersMustBeConsecutive() {
        assertThrows(IllegalArgumentException.class, () -> new Gapped().applyTo(emptyDocument(), 1));
    }

    @Test
    void latestVersionIsTheHighestDeclaredNumberOrOneWithNoSteps() {
        assertEquals(4, new StepsForTest().getLatestVersion());
        assertEquals(ConfigSteps.BASE_VERSION, ConfigSteps.NONE.getLatestVersion());
    }

    private static ConfigDocument emptyDocument() {
        return new ConfigDocument(Collections.emptyMap());
    }
}
