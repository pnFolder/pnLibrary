package org.pnfolder.pnlibrary.core.commands.cooldowns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.cooldowns.CooldownService;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry;
import org.pnfolder.pnlibrary.core.cooldowns.InMemoryCooldowns;

class CooldownsCommandTest {
    @Test
    @DisplayName("running cooldowns are listed across plugins and modules longest first")
    void runningCooldownsAreListedAcrossPluginsAndModulesLongestFirst() {
        UUID player = UUID.randomUUID();
        CooldownService shop = new InMemoryCooldowns();
        shop.set(player, "home", Duration.ofSeconds(30));
        CooldownService clans = new InMemoryCooldowns();
        clans.set(player, "war", Duration.ofSeconds(90));
        PluginRegistry plugins = registry(new String[] {"shop", "clans"}, new CooldownService[] {shop, clans});

        List<RunningCooldown> running = CooldownsCommand.runningCooldowns(plugins, player);

        List<String> shown = new ArrayList<String>();
        for (RunningCooldown cooldown : running) {
            shown.add(cooldown.getOwner() + " " + cooldown.getAction());
        }
        assertEquals(Arrays.asList("clans/main war", "shop/main home"), shown);
    }

    private PluginRegistry registry(String[] names, CooldownService[] cooldowns) {
        List<PluginRegistration> registrations = new ArrayList<PluginRegistration>();
        for (int index = 0; index < names.length; index++) {
            ModuleContext module = mock(ModuleContext.class);
            when(module.getId()).thenReturn(ModuleId.of("main"));
            when(module.getCooldowns()).thenReturn(cooldowns[index]);
            PluginRegistration registration = mock(PluginRegistration.class);
            when(registration.getId()).thenReturn(PluginId.of(names[index]));
            when(registration.all()).thenReturn(Arrays.asList(module));
            registrations.add(registration);
        }
        PluginRegistry registry = mock(PluginRegistry.class);
        when(registry.all()).thenReturn(registrations);
        return registry;
    }
}
