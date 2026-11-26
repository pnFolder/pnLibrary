package org.pnfolder.pnlibrary.core.plugin.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.compat.MinecraftSupport;
import org.pnfolder.pnlibrary.api.compat.declaration.MinecraftVersions;
import org.pnfolder.pnlibrary.api.compat.declaration.NewerAction;
import org.pnfolder.pnlibrary.api.compat.declaration.UnsupportedAction;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState;
import org.pnfolder.pnlibrary.api.plugin.control.PluginModule;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.core.commands.modules.MinecraftForcible;
import org.pnfolder.pnlibrary.core.plugin.LoadedPlugins;
import org.pnfolder.pnlibrary.core.plugin.PluginRegistryFixture;

class ModuleControlMinecraftTest {
    private static final class Hooked implements PluginModule {
    }

    private static LoadedPlugins registryOn(String minecraftVersion) {
        return PluginRegistryFixture.builder().minecraftVersion(minecraftVersion).build();
    }

    @Test
    void aModuleOutsideItsMinecraftVersionsIsSwitchedOffWithTheReasonAndAnAdministratorCanForceIt() {
        LoadedPlugins registry = registryOn("1.21.11");

        ModuleContext context = registry.register(new Object()).registerModule("shop", new Hooked(),
            it -> it.supportedMinecraft(MinecraftVersions.between(MinecraftVersion.V1_16_5, MinecraftVersion.V1_20_4)));
        // the version 1.21.11 is newer than 1.20.4: the default only warns
        assertEquals(ModuleState.ENABLED, context.getControl().getState());
        assertEquals(MinecraftSupport.NEWER_THAN_DECLARED, context.getMinecraft().getSupport());

        ModuleContext strict = registry.register(new Object()).registerModule("strict", new Hooked(), it -> {
            it.supportedMinecraft(MinecraftVersions.only("1.20.1"));
            it.onNewerMinecraft(NewerAction.BLOCK);
        });
        assertEquals(ModuleState.DISABLED, strict.getControl().getState());
        assertTrue(strict.getMinecraft().isBlocked());
        assertNotNull(strict.getControl().getLastError());

        ((MinecraftForcible) strict).forceMinecraft(true);
        strict.getControl().enable();
        assertEquals(ModuleState.ENABLED, strict.getControl().getState());
        registry.close();
    }

    @Test
    void aModuleWithoutHooksAndAStoppedPluginRefuseToRegisterOutsideTheVersions() {
        LoadedPlugins registry = registryOn("1.12.2");

        assertThrows(IllegalStateException.class, () -> registry.register(new Object()).registerModule("plain",
            it -> it.supportedMinecraft(MinecraftVersions.from(MinecraftVersion.V1_16_5))));
        assertThrows(IllegalStateException.class, () -> registry.register(new Object()).registerModule("stop", new Hooked(), it -> {
            it.supportedMinecraft(MinecraftVersions.from("1.16.5"));
            it.onUnsupportedMinecraft(UnsupportedAction.STOP_PLUGIN);
        }));
        ModuleContext warned = registry.register(new Object()).registerModule("soft", new Hooked(), it -> {
            it.supportedMinecraft(MinecraftVersions.from("1.16.5"));
            it.onUnsupportedMinecraft(UnsupportedAction.WARN_ONLY);
        });
        assertEquals(ModuleState.ENABLED, warned.getControl().getState());
        registry.close();
    }

    @Test
    void aModuleWithoutItsOwnVersionsUsesThePlugins() {
        LoadedPlugins registry = registryOn("1.12.2");
        PluginRegistration plugin = registry.register(new Object());
        plugin.supportedMinecraft(MinecraftVersions.from(MinecraftVersion.V1_16_5));

        ModuleContext inherited = plugin.registerModule("a", new Hooked(), it -> { });
        ModuleContext own = plugin.registerModule("b", new Hooked(),
            it -> it.supportedMinecraft(MinecraftVersions.between(MinecraftVersion.V1_8_8, MinecraftVersion.V1_12_2)));

        assertEquals(ModuleState.DISABLED, inherited.getControl().getState());
        assertEquals("1.16.5+", inherited.getMinecraft().getDeclared());
        assertEquals(ModuleState.ENABLED, own.getControl().getState());
        registry.close();
    }
}
