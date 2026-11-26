package org.pnfolder.pnlibrary.core.commands.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState;
import org.pnfolder.pnlibrary.api.plugin.control.PluginModule;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.commands.engine.CommandRoute;
import org.pnfolder.pnlibrary.core.commands.engine.CommandTreeRouter;
import org.pnfolder.pnlibrary.core.commands.engine.ExecutableRoute;
import org.pnfolder.pnlibrary.core.plugin.LoadedPlugins;
import org.pnfolder.pnlibrary.core.plugin.PluginRegistryFixture;

class ModulesCommandTest {
    private static final class Switchable implements PluginModule {
    }

    private final LoadedPlugins registry = PluginRegistryFixture.registry();
    private final PnLibrary library = mock(PnLibrary.class);
    private final List<String> sent = new ArrayList<>();
    private final CommandSender sender = new CommandSender() {
        @Override
        public @NotNull Pointers pointers() {
            return AudienceExtensions.senderPointers("Console", permission -> true);
        }

        @Override
        public void sendMessage(@NotNull Component message) {
            sent.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
    };
    private final CommandDefinition definition;

    ModulesCommandTest() {
        when(library.getPlugins()).thenReturn(registry);
        definition = new ModulesCommand(library).definition();
    }

    private String run(String... words) {
        sent.clear();
        CommandRoute route = new CommandTreeRouter().route(definition,
            new CommandContext(sender, Arrays.asList(words), "modules", String.join(" ", words)));
        ExecutableRoute executable = (ExecutableRoute) route;
        executable.getNode().getExecution().execute(executable.getContext()).toCompletableFuture().join();
        return String.join("\n", sent);
    }

    @Test
    void anEmptyServerSaysThereAreNoModules() {
        assertTrue(run().contains("Модулей пока нет"));
        registry.close();
    }

    @Test
    void theListShowsEveryModuleWithItsStateAndAModuleWithoutHooksIsMarked() {
        PluginRegistration plugin = registry.register(new Object());
        ModuleContext shop = plugin.registerModule("shop", new Switchable());
        plugin.registerModule("plain");

        String text = run();

        assertTrue(text.contains(":shop"), text);
        assertTrue(text.contains("работает"), text);
        assertTrue(text.contains("без переключателя"), text);
        assertEquals(ModuleState.ENABLED, shop.getControl().getState());
        registry.close();
    }

    @Test
    void disableAndEnableSwitchTheModuleAndViewShowsTheState() {
        ModuleContext module = registry.register(new Object()).registerModule("shop", new Switchable());
        String name = ModuleLabels.named(library).get(0).getName();

        run("disable", name);
        assertEquals(ModuleState.DISABLED, module.getControl().getState());
        assertTrue(run("view", name).contains("выключен"));

        run("enable", name);
        assertEquals(ModuleState.ENABLED, module.getControl().getState());
        registry.close();
    }

    @Test
    void anUnknownModuleIsRefusedWithAPointerToTheList() {
        String text = run("view", "nothing:here");

        assertTrue(text.contains("Такого модуля нет"), text);
        registry.close();
    }

    @Test
    void autoOffKeepsTheModuleFromStartingWithTheServer() {
        ModuleContext module = registry.register(new Object()).registerModule("shop", new Switchable());
        String name = ModuleLabels.named(library).get(0).getName();

        run("auto", name, "off");

        assertEquals(false, module.getControl().getAutoStart());
        assertTrue(run().contains("не включается сам"));
        registry.close();
    }

    @Test
    void pluginsListsEachPluginWithItsModules() {
        registry.register(new Object()).registerModule("shop", new Switchable());

        String text = run("plugins");

        assertTrue(text.contains("модулей 1"), text);
        registry.close();
    }
}
