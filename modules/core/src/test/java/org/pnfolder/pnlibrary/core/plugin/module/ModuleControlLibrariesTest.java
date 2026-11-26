package org.pnfolder.pnlibrary.core.plugin.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;
import org.pnfolder.pnlibrary.api.libraries.state.LibraryState;
import org.pnfolder.pnlibrary.api.libraries.state.LibraryStatus;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState;
import org.pnfolder.pnlibrary.api.plugin.control.PluginModule;
import org.pnfolder.pnlibrary.core.plugin.LoadedPlugins;
import org.pnfolder.pnlibrary.core.plugin.PluginRegistryFixture;

class ModuleControlLibrariesTest {
    private static final class WithLibrary implements PluginModule {
        private final ModuleLibrary library;
        private boolean enabled;

        private WithLibrary(ModuleLibrary library) {
            this.library = library;
        }

        @Override
        public @NotNull List<ModuleLibrary> libraries() {
            return Collections.singletonList(library);
        }

        @Override
        public void onEnable(@NotNull ModuleContext context) {
            enabled = true;
        }
    }

    private static LoadedPlugins registryFetching(LibraryFetcher fetcher) {
        return PluginRegistryFixture.builder().libraryFetcher(fetcher).build();
    }

    @Test
    void aRequiredLibraryThatCannotBeLoadedFailsTheModuleAndAnOptionalOneDoesNot() {
        WithLibrary required = new WithLibrary(ModuleLibrary.of("com.example:missing:1"));
        WithLibrary optional = new WithLibrary(ModuleLibrary.builder("com.example", "missing", "1").optional().build());
        LoadedPlugins registry = registryFetching((loader, owner, plugin, library) -> {
            throw new IllegalStateException("offline");
        });

        ModuleContext failed = registry.register(new Object()).registerModule("needs", required);
        ModuleContext started = registry.register(new Object()).registerModule("wants", optional);

        assertEquals(ModuleState.FAILED, failed.getControl().getState());
        assertFalse(required.enabled);
        assertNotNull(failed.getControl().getLastError());
        assertEquals(ModuleState.ENABLED, started.getControl().getState());
        assertTrue(optional.enabled);
        registry.close();
    }

    @Test
    void librariesDeclaredInTheRegistrationBuilderAreLoadedBeforeTheModuleStarts() {
        List<String> fetched = new ArrayList<>();
        LoadedPlugins registry = registryFetching((loader, owner, plugin, library) -> {
            fetched.add(library.getCoordinates());
            return null;
        });

        ModuleContext context = registry.register(new Object()).registerModule("shop", setup -> {
            setup.library("org.xerial:sqlite-jdbc:3.46.0.0");
            setup.library("org.xerial:sqlite-jdbc:3.46.0.0");
            setup.library("com.example", "extra", "1", it -> it.optional().title("Extra"));
        });

        assertEquals(Arrays.asList("org.xerial:sqlite-jdbc:3.46.0.0", "com.example:extra:1"), fetched);
        assertEquals(2, context.getLibraries().getLoaded().size());
        registry.close();
    }

    @Test
    void defaultsApplyToLibrariesDeclaredByCoordinatesAndAFalseConditionSkipsTheDownload() {
        List<ModuleLibrary> fetched = new ArrayList<>();
        LoadedPlugins registry = registryFetching((loader, owner, plugin, library) -> {
            fetched.add(library);
            return null;
        });

        registry.register(new Object()).registerModule("shop", setup -> {
            setup.libraryDefaults(it -> it.source(s -> s.repository("https://mirror.example.org/maven/")).mavenCentral(false).optional());
            setup.library("org.xerial:sqlite-jdbc:3.46.0.0");
            setup.library(ModuleLibrary.of("com.example:skipped:1"), () -> false);
        });

        assertEquals(1, fetched.size());
        assertEquals("org.xerial:sqlite-jdbc:3.46.0.0", fetched.get(0).getCoordinates());
        assertFalse(fetched.get(0).isMavenCentral());
        assertFalse(fetched.get(0).isRequired());
        assertEquals(1, fetched.get(0).getSources().size());
        assertEquals("https://mirror.example.org/maven/", fetched.get(0).getSources().get(0).getUrl());
        registry.close();
    }

    @Test
    void anAdministratorCanSwitchALibraryOffAndOnAndRetryAFailedOne() {
        AtomicBoolean offline = new AtomicBoolean(true);
        List<String> fetched = new ArrayList<>();
        LoadedPlugins registry = registryFetching((loader, owner, plugin, library) -> {
            if (offline.get() && library.getArtifact().equals("flaky")) {
                throw new IllegalStateException("offline");
            }
            fetched.add(library.getCoordinates());
            return null;
        });
        ModuleContext context = registry.register(new Object()).registerModule("shop", it -> {
            it.library(ModuleLibrary.builder("g", "flaky", "1").optional().build());
            it.library("g:steady:1");
        });
        ModuleLibrary flaky = ModuleLibrary.builder("g", "flaky", "1").build();
        ModuleLibrary steady = ModuleLibrary.of("g:steady:1");

        assertEquals(LibraryState.FAILED, context.getLibraries().status(flaky).getState());
        assertEquals("offline", context.getLibraries().status(flaky).getError());
        assertEquals(LibraryState.FAILED, context.getLibraries().retry(flaky).getState());
        offline.set(false);
        assertEquals(LibraryState.LOADED, context.getLibraries().retry(flaky).getState());

        assertSwitching(context, steady);
        assertEquals(Arrays.asList("g:steady:1", "g:flaky:1"), fetched);
        registry.close();
    }

    private static void assertSwitching(ModuleContext context, ModuleLibrary steady) {
        LibraryStatus off = context.getLibraries().setAutoLoad(steady, false);
        assertTrue(off.isRestartRequired());
        assertFalse(off.isAutoLoad());
        assertEquals(LibraryState.LOADED, off.getState());
        LibraryStatus on = context.getLibraries().setAutoLoad(steady, true);
        assertFalse(on.isRestartRequired());
        assertTrue(on.isAutoLoad());
    }

    @Test
    void aLibrarySwitchedOffIsNotLoadedAtTheNextRegistration() {
        List<String> fetched = new ArrayList<>();
        LoadedPlugins registry = registryFetching((loader, owner, plugin, library) -> {
            fetched.add(library.getCoordinates());
            return null;
        });
        registry.register(new Object()).registerModule("shop", it -> it.library("g:a:1"))
            .getLibraries().setAutoLoad(ModuleLibrary.of("g:a:1"), false);
        fetched.clear();

        ModuleContext again = registry.register(new Object()).registerModule("shop", it -> it.library("g:a:1"));

        assertEquals(LibraryState.DISABLED, again.getLibraries().status(ModuleLibrary.of("g:a:1")).getState());
        assertTrue(fetched.isEmpty());
        registry.close();
    }

    @Test
    void aRequiredLibraryDeclaredInTheBuilderStopsTheRegistrationAndAnOptionalOneOnlyWarns() {
        LoadedPlugins registry = registryFetching((loader, owner, plugin, library) -> {
            throw new IllegalStateException("offline");
        });

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> registry.register(new Object()).registerModule("needs", it -> it.library("com.example:missing:1")));
        ModuleContext started = registry.register(new Object()).registerModule("wants",
            it -> it.library(ModuleLibrary.builder("com.example", "missing", "1").optional().build()));

        assertNotNull(failure.getMessage());
        assertTrue(started.getLibraries().getLoaded().isEmpty());
        registry.close();
    }
}
