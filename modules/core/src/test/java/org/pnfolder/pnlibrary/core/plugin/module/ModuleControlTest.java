package org.pnfolder.pnlibrary.core.plugin.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;
import org.pnfolder.pnlibrary.api.libraries.state.LibraryUsage;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState;
import org.pnfolder.pnlibrary.api.plugin.control.PluginModule;
import org.pnfolder.pnlibrary.core.plugin.LoadedPlugins;
import org.pnfolder.pnlibrary.core.plugin.PluginRegistryFixture;

class ModuleControlTest {
    private static final class Recording implements PluginModule {
        private final List<String> calls = new ArrayList<>();
        private final String failOn;
        private final boolean inPlace;

        private Recording() {
            this(null, false);
        }

        private Recording(String failOn, boolean inPlace) {
            this.failOn = failOn;
            this.inPlace = inPlace;
        }

        private void step(String name) {
            calls.add(name);
            if (name.equals(failOn)) {
                throw new IllegalStateException("boom in " + name);
            }
        }

        @Override
        public void onEnable(@NotNull ModuleContext context) {
            step("enable");
        }

        @Override
        public void onDisable(@NotNull ModuleContext context) {
            step("disable");
        }

        @Override
        public boolean onReload(@NotNull ModuleContext context) {
            step("reload");
            return inPlace;
        }
    }

    @Test
    void aModuleStartsAtRegistrationAndCanBeDisabledEnabledAndRestarted() {
        Recording module = new Recording();
        LoadedPlugins registry = PluginRegistryFixture.registry();
        ModuleContext context = registry.register(new Object()).registerModule("shop", module);

        assertEquals(ModuleState.ENABLED, context.getControl().getState());
        context.getControl().disable();
        assertEquals(ModuleState.DISABLED, context.getControl().getState());
        context.getControl().enable();
        context.getControl().restart();
        assertEquals(Arrays.asList("enable", "disable", "enable", "disable", "enable"), module.calls);
        assertEquals(ModuleState.ENABLED, context.getControl().getState());
        registry.close();
    }

    @Test
    void reloadRestartsAModuleThatCannotReloadInPlace() {
        Recording stays = new Recording(null, true);
        Recording restarts = new Recording(null, false);
        LoadedPlugins registry = PluginRegistryFixture.registry();
        registry.register(new Object()).registerModule("one", stays).getControl().reload();
        registry.register(new Object()).registerModule("two", restarts).getControl().reload();

        assertEquals(Arrays.asList("enable", "reload"), stays.calls);
        assertEquals(Arrays.asList("enable", "reload", "disable", "enable"), restarts.calls);
        registry.close();
    }

    @Test
    void aFailingHookLeavesTheModuleFailedWithTheErrorAndEnablingTriesAgain() {
        Recording module = new Recording("enable", false);
        LoadedPlugins registry = PluginRegistryFixture.registry();
        ModuleContext context = registry.register(new Object()).registerModule("shop", module);

        assertEquals(ModuleState.FAILED, context.getControl().getState());
        assertNotNull(context.getControl().getLastError());
        context.getControl().disable();
        assertEquals(ModuleState.DISABLED, context.getControl().getState());
        assertNull(context.getControl().getLastError());
        registry.close();
    }

    @Test
    void aModuleThatAnAdministratorKeepsOffDoesNotStartWithTheServer(@TempDir Path directory) {
        Path file = directory.resolve("modules").resolve("preferences.json");
        ModulePreferences first = new ModulePreferences(file);
        first.setAutoStart("shop", false);

        assertFalse(new ModulePreferences(file).autoStart("shop"));
        assertTrue(new ModulePreferences(file).autoStart("other"));
        first.setAutoStart("shop", true);
        assertTrue(new ModulePreferences(file).autoStart("shop"));
    }

    @Test
    void aModuleWithoutHooksIsListedButCannotBeSwitched() {
        LoadedPlugins registry = PluginRegistryFixture.registry();
        ModuleContext context = registry.register(new Object()).registerModule("plain");

        assertFalse(context.getControl().isControllable());
        assertEquals(ModuleState.ENABLED, context.getControl().getState());
        assertThrows(UnsupportedOperationException.class, () -> context.getControl().disable());
        registry.close();
    }

    @Test
    void closingThePluginRunsTheDisableHookOnce() {
        Recording module = new Recording();
        LoadedPlugins registry = PluginRegistryFixture.registry();
        registry.register(new Object()).registerModule("shop", module);
        registry.close();

        assertEquals(Arrays.asList("enable", "disable"), module.calls);
    }

    @Test
    void theLibraryListsEveryModuleAndEveryLibraryWithWhoRegisteredIt() {
        LoadedPlugins registry = PluginRegistryFixture.registry();
        PluginRegistration plugin = registry.register(new Object());
        plugin.registerModule("one", new Recording()).getLibraries().load(ModuleLibrary.of("g:a:1"));
        plugin.registerModule("two", new Recording()).getLibraries().load(ModuleLibrary.of("g:a:2"));

        assertEquals(2, registry.allModules().size());
        List<String> modules = new ArrayList<>();
        for (LibraryUsage usage : registry.libraryUsage()) {
            modules.add(usage.getModule() == null ? null : usage.getModule().getValue());
        }
        Collections.sort(modules);
        assertEquals(Arrays.asList("one", "two"), modules);
        assertEquals(2, plugin.getAllLibraries().size());
        registry.close();
    }
}
