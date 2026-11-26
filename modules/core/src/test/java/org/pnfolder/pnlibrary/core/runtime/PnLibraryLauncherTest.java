package org.pnfolder.pnlibrary.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticContainer;
import org.pnfolder.pnlibrary.api.expansions.AbstractExpansion;
import org.pnfolder.pnlibrary.api.expansions.ExpansionState;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider;
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRuntimeContext;
import org.pnfolder.pnlibrary.localization.MinecraftLocalization;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;

class PnLibraryLauncherTest {

    @Test
    void bootstrapCreatesOneProcessRuntime() {
        DummyPlugin dummyOwner = new DummyPlugin("pnMarket");
        DummyPlatformAdapter dummyPlatform = new DummyPlatformAdapter();

        PnLibrary lib1 = PnLibraryLauncher.bootstrap(dummyOwner, PlatformRuntimeContext.from(dummyPlatform));
        PnLibrary lib2 = PnLibraryLauncher.bootstrap(new DummyPlugin("another"), PlatformRuntimeContext.from(new DummyPlatformAdapter()));

        assertSame(lib1, lib2);
        assertEquals(dummyOwner, lib1.getOwner());
        assertSame(lib1, dummyPlatform.boundLibrary);

        lib1.close();
        assertTrue(lib1.isClosed());
        assertNull(PnLibraryProvider.getOrNull());
    }

    @Test
    void oldSettingsFilesAreImportedAtStartupAndTheSettingsCommandIsMounted() throws IOException {
        DummyPlatformAdapter platform = new DummyPlatformAdapter();
        Files.write(platform.getDataFolder().resolve("config.yml"),
            "privacy: true\nhistory-retention-days: 45\n".getBytes(StandardCharsets.UTF_8));

        PnLibrary library = PnLibraryLauncher.bootstrap(new DummyPlugin("pnMarket"), PlatformRuntimeContext.from(platform));
        try {
            assertTrue(library.getConfiguration().isPrivacy());
            assertEquals(45, library.getConfiguration().getHistoryRetentionDays());
            assertTrue(Files.exists(platform.getDataFolder().resolve("config.yml.old")));
            assertTrue(Files.exists(platform.getDataFolder().resolve("data/settings.json")));
            assertNotNull(RuntimeBridge.mountableCommand(library, "settings"));
        } finally {
            library.close();
        }
    }

    @Test
    void theLocalizationLoaderIsNeitherAServiceNorAPublicExpansion() {
        PnLibrary library = PnLibraryLauncher.bootstrap(new DummyPlugin("pnMarket"), PlatformRuntimeContext.from(new DummyPlatformAdapter()));
        try {
            assertNull(library.getServices().get(MinecraftLocalization.class));
            assertNull(library.getExpansions().get("localization"));
        } finally {
            library.close();
        }
    }

    @Test
    void theLibraryWideExpansionsRegisterAndListAndHoldNothingOfTheLibrarysOwn() throws Exception {
        PnLibrary library = PnLibraryLauncher.bootstrap(new DummyPlugin("pnMarket"), PlatformRuntimeContext.from(new DummyPlatformAdapter()));
        try {
            assertNull(library.getExpansions().get("localization"));
            assertTrue(library.getExpansions().all().isEmpty());
            assertNull(library.getServices().get(MinecraftLocalization.class));

            Weather weather = new Weather();
            AutoCloseable handle = library.getExpansions().register(weather);
            assertSame(weather, library.getExpansions().get("weather"));
            assertSame(weather, library.getExpansions().get(weather.getClass()));
            assertEquals(ExpansionState.ENABLED, weather.getState());

            handle.close();
            assertNull(library.getExpansions().get("weather"));
            assertEquals(ExpansionState.DISABLED, weather.getState());
        } finally {
            library.close();
        }
    }

    @Test
    void diagnosticsContainerFailureIsIsolated() {
        DiagnosticsRegistry registry = new DiagnosticsRegistry();

        // Container 1 succeeds
        registry.register("pnMarket", DiagnosticContainer.builder("auction")
            .snapshot(() -> Collections.<String, Object>singletonMap("lots", 10))
            .build());

        // Container 2 throws an exception
        registry.register("pnMarket", DiagnosticContainer.builder("broken")
            .snapshot(() -> {
                throw new RuntimeException("Database error");
            })
            .build());

        Map<String, Object> snap = registry.snapshot("pnMarket");
        @SuppressWarnings("unchecked")
        Map<String, Object> pluginData = (Map<String, Object>) (snap.get("pnmarket") != null ? snap.get("pnmarket") : snap.get("pnMarket"));
        assertNotNull(pluginData);
        @SuppressWarnings("unchecked")
        Map<String, Object> contribs = (Map<String, Object>) pluginData.get("contributors");

        assertNotNull(contribs.get("auction"));
        assertNotNull(contribs.get("broken"));

        @SuppressWarnings("unchecked")
        Map<String, Object> brokenData = (Map<String, Object>) contribs.get("broken");
        assertTrue(brokenData.containsKey("collectionError"));
    }

    private static final class Weather extends AbstractExpansion {
        private Weather() {
            super("weather");
        }

        @Override
        protected void onEnable() {
        }

        @Override
        protected void onDisable() {
        }
    }

    private static final class DummyPlugin {
        private final String name;

        private DummyPlugin(String name) {
            this.name = name;
        }
    }

    private static final class DummyPlatformAdapter implements PlatformAdapter {
        private PnLibrary boundLibrary;
        private final Path dataPath;

        private DummyPlatformAdapter() {
            try {
                dataPath = Files.createTempDirectory("pnlibrary-bootstrap-test");
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }

        @Override
        public @NotNull PlatformType getType() {
            return PlatformType.BUKKIT;
        }

        @Override
        public @NotNull String getId() {
            return "dummy";
        }

        @Override
        public Path getDataFolder() {
            return dataPath;
        }

        @Override
        public void bind(@NotNull PnLibrary library) {
            assertSame(library, PnLibraryProvider.get());
            boundLibrary = library;
        }

        @Override
        public @NotNull Map<String, Object> diagnosticDetails(boolean includeSensitive) {
            return Collections.<String, Object>singletonMap("test", true);
        }

        @Override
        public void executeGlobal(@NotNull Runnable task) {
            task.run();
        }

        @Override
        public void executeReply(@NotNull Object recipient, @NotNull Runnable task) {
            task.run();
        }

        @Override
        public void close() {
        }
    }
}
