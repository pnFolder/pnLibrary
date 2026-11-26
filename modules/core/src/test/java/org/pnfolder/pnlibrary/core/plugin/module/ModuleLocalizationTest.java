package org.pnfolder.pnlibrary.core.plugin.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.expansions.ExpansionState;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.core.expansions.ExpansionRegistry;
import org.pnfolder.pnlibrary.core.expansions.LocalizationExpansion;
import org.pnfolder.pnlibrary.core.plugin.LoadedPlugins;
import org.pnfolder.pnlibrary.core.plugin.PluginRegistryFixture;
import org.pnfolder.pnlibrary.localization.MinecraftLocalization;
import org.pnfolder.pnlibrary.localization.TranslationException;
import org.pnfolder.pnlibrary.localization.TranslationIndex;
import org.pnfolder.pnlibrary.localization.TranslationMatch;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout;

class ModuleLocalizationTest {
    @TempDir
    Path cache;

    private final ExpansionRegistry expansions = new ExpansionRegistry((expansion, error) -> {
        throw new AssertionError(error);
    });
    private final ExpansionRegistry library = new ExpansionRegistry((expansion, error) -> {
        throw new AssertionError(error);
    });
    private LocalizationExpansion localization;
    private final KeyboardLayout dvorak = KeyboardLayout.of("module-test-dvorak", "',.pyfgcrl/=\\aoeuidhtns-;qjkxbmwv`[");
    private final Object owner = new Object();
    private final LoadedPlugins registry = PluginRegistryFixture.builder().expansions(expansions).libraryExpansions(library).build();

    @BeforeEach
    void start() {
        localization = new LocalizationExpansion(cache);
        library.register(localization);
    }

    @AfterEach
    void stop() {
        expansions.close();
        library.close();
    }

    private ModuleContext module(String id) {
        return PluginRegistryFixture.registerModule(registry, owner, id, it -> { });
    }

    /** A one-entry English index reading queries against the layouts the module's view exposes. */
    private static TranslationIndex<String> apples(ModuleContext context) {
        List<TranslationMatch<String>> matches = new ArrayList<>();
        matches.add(new TranslationMatch<>("item.apple", "Apple", "apple"));
        return TranslationIndex.of(matches, context.getLocalization().getLayouts());
    }

    @Test
    void aLayoutOneModuleRegistersIsReadByAnotherUntilTheFirstModuleCloses() {
        ModuleContext alpha = module("alpha");
        ModuleContext beta = module("beta");
        TranslationIndex<String> applesOfBeta = apples(beta);
        assertNull(applesOfBeta.best("arrpd"));

        alpha.getLocalization().getLayouts().register(dvorak);
        assertEquals("item.apple", applesOfBeta.best("arrpd").getKey());

        alpha.close();
        assertNull(applesOfBeta.best("arrpd"));
        assertEquals(KeyboardLayout.builtIn, beta.getLocalization().getLayouts().all());
    }

    @Test
    void closingAModuleKeepsTheLayoutsOfTheOthers() {
        ModuleContext alpha = module("alpha");
        ModuleContext beta = module("beta");
        KeyboardLayout other = KeyboardLayout.of("module-test-other", "abcdefghijklmnopqrstuvwxyz012345678");
        alpha.getLocalization().getLayouts().register(dvorak);
        beta.getLocalization().getLayouts().register(other);

        alpha.close();

        List<KeyboardLayout> expected = new ArrayList<>(KeyboardLayout.builtIn);
        expected.add(other);
        assertEquals(expected, beta.getLocalization().getLayouts().all());
    }

    @Test
    void layoutIdsStayUniqueAcrossModules() {
        ModuleContext alpha = module("alpha");
        ModuleContext beta = module("beta");
        alpha.getLocalization().getLayouts().register(dvorak);

        assertThrows(IllegalArgumentException.class, () -> beta.getLocalization().getLayouts().register(dvorak));
        assertThrows(IllegalArgumentException.class, () -> alpha.getLocalization().getLayouts().register(dvorak));
        assertThrows(IllegalArgumentException.class, () -> beta.getLocalization().getLayouts().register(KeyboardLayout.RU));
    }

    @Test
    void theHandleRemovesALayoutEarlyAndTheModuleMayRegisterItAgain() throws Exception {
        ModuleContext alpha = module("alpha");
        AutoCloseable handle = alpha.getLocalization().getLayouts().register(dvorak);

        handle.close();
        handle.close();
        assertEquals(KeyboardLayout.builtIn, alpha.getLocalization().getLayouts().all());

        alpha.getLocalization().getLayouts().register(dvorak);
        assertTrue(alpha.getLocalization().getLayouts().all().contains(dvorak));
    }

    @Test
    void aModuleCannotCloseTheSharedLoaderThroughItsView() throws Exception {
        ModuleContext alpha = module("alpha");
        ModuleContext beta = module("beta");

        alpha.getLocalization().close();
        alpha.getLocalization().getLayouts().close();

        assertEquals(ExpansionState.ENABLED, localization.getState());
        ExecutionException failure = assertThrows(ExecutionException.class,
            () -> beta.getLocalization().availableLocales(MinecraftVersion.UNKNOWN).toCompletableFuture().get());
        assertEquals(TranslationException.Reason.UNSUPPORTED_VERSION, ((TranslationException) failure.getCause()).getReason());
    }

    @Test
    void localizationIsReachableOnlyThroughTheModuleContext() {
        ModuleContext alpha = module("alpha");

        assertNull(alpha.getServices().get(MinecraftLocalization.class));
        assertTrue(alpha.getServices().all(MinecraftLocalization.class).isEmpty());
        assertSame(alpha.getLocalization(), alpha.getLocalization());
        assertNotSame(alpha.getLocalization(), module("beta").getLocalization());
    }

    @Test
    void theLibrarysLocalizationExpansionIsInvisibleAndUncontrollableToAPlugin() {
        ModuleContext alpha = module("alpha");

        assertNull(alpha.getExpansions().get("localization"));
        assertNull(alpha.getExpansions().get(LocalizationExpansion.class));
        assertTrue(alpha.getExpansions().all().isEmpty());
        assertEquals(ExpansionState.ENABLED, localization.getState());
        assertSame(localization, library.get("localization"));
    }

    @Test
    void aViewReportsClosedOnceTheLibraryHasShutDown() {
        ModuleContext alpha = module("alpha");
        MinecraftLocalization view = alpha.getLocalization();

        library.close();

        assertEquals(TranslationException.Reason.CLOSED,
            assertThrows(TranslationException.class, view::availableVersions).getReason());
        assertThrows(IllegalStateException.class, () -> view.getLayouts().register(dvorak));
    }

    @Test
    void aClosedModuleHasNoLocalization() {
        ModuleContext alpha = module("alpha");
        MinecraftLocalization view = alpha.getLocalization();
        alpha.close();

        assertThrows(IllegalStateException.class, alpha::getLocalization);
        assertThrows(IllegalStateException.class, () -> view.getLayouts().register(dvorak));
    }

    @Test
    void aPlatformWithoutALoaderSaysSo() {
        ModuleContext bare = PluginRegistryFixture.registerModule(PluginRegistryFixture.registry(), owner, "alpha", it -> { });

        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class, bare::getLocalization);
        assertEquals("Localization is not available on this platform", failure.getMessage());
    }
}
