package org.pnfolder.pnlibrary.bukkit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Logger;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.UpdateProducts;
import org.pnfolder.pnlibrary.api.updates.UpdateService;
import org.pnfolder.pnlibrary.bukkit.audience.BukkitAdventureTransport;
import org.pnfolder.pnlibrary.bukkit.audience.BukkitAudienceAdapter;
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender;
import org.pnfolder.pnlibrary.bukkit.commands.BukkitControlCommand;

class PlayerCommandReplyTest {
    private PnLoggerBuffer buffer() {
        return new PnLoggerBuffer(Logger.getAnonymousLogger(), () -> null);
    }

    private Plugin plugin() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getServer()).thenReturn(mock(Server.class));
        return plugin;
    }

    @Test
    void paperCommandReplyTargetsTheActualPlayerAndPreservesClickEvents() {
        Player player = mock(Player.class, withSettings().extraInterfaces(Audience.class));
        BukkitAudiences provider = mock(BukkitAudiences.class);
        BukkitAdventureTransport service = new BukkitAdventureTransport(plugin(), buffer(), provider);
        Component message = Component.text("[История]").clickEvent(ClickEvent.runCommand("/pn updates history"));
        try {
            service.player(player).sendMessage(message);
            verify((Audience) player).sendMessage(message);
            verifyNoInteractions(provider);
            verify(player, never()).sendMessage(anyString());
        } finally {
            service.close();
        }
    }

    @Test
    void emptyUuidAudienceDoesNotSilentlySwallowALegacyPlayerReply() {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        BukkitAudiences provider = mock(BukkitAudiences.class);
        when(provider.player(id)).thenReturn(Audience.empty());
        BukkitAdventureTransport service = new BukkitAdventureTransport(plugin(), buffer(), provider);
        try {
            service.player(player).sendMessage(Component.text("Каталог pnLibrary"));
            verify(player).sendMessage("Каталог pnLibrary");
        } finally {
            service.close();
        }
    }

    @Test
    void rootUpdatesListAndAvailableCommandsReplyToTheInvokingPlayerEvenForEmptyCatalog() {
        Plugin plugin = plugin();
        Player player = mock(Player.class, withSettings().extraInterfaces(Audience.class));
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Alice");
        BukkitAudiences provider = mock(BukkitAudiences.class);
        BukkitAdventureTransport service = new BukkitAdventureTransport(plugin, buffer(), provider);
        PnLibrary library = mock(PnLibrary.class);
        UpdateService updates = mock(UpdateService.class);
        when(library.getUpdates()).thenReturn(updates);
        UpdateProducts products = mock(UpdateProducts.class);
        when(updates.getProducts()).thenReturn(products);
        when(products.all()).thenReturn(Collections.emptyList());
        CommandDefinition definition = new BukkitControlCommand(plugin, library, mock(CurrencyProviderRegistry.class)).definition();
        CommandNode root = definition.getRoot();
        CommandNode catalog = find(root, "updates");
        BukkitCommandSender sender = new BukkitCommandSender(player, new BukkitAudienceAdapter(service).of(player));
        CommandContext context = new CommandContext(sender, Collections.emptyList(), "pnlibrary:pn", "");
        try {
            for (CommandNode node : Arrays.asList(root, catalog, find(catalog, "list"), find(catalog, "available"))) {
                node.getExecution().execute(context).toCompletableFuture().join();
            }
            ArgumentCaptor<Component> messages = ArgumentCaptor.forClass(Component.class);
            verify((Audience) player, atLeast(12)).sendMessage(messages.capture());
            assertTrue(messages.getAllValues().stream().anyMatch(component -> hasClick(component, "/pn updates history")));
            verifyNoInteractions(provider);
        } finally {
            service.close();
        }
    }

    private static CommandNode find(CommandNode parent, String name) {
        for (CommandNode child : parent.getChildren()) {
            if (child.getName().equals(name)) {
                return child;
            }
        }
        throw new IllegalStateException("No node " + name);
    }

    private static boolean hasClick(Component component, String command) {
        if (component.clickEvent() != null && command.equals(component.clickEvent().value())) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasClick(child, command)) {
                return true;
            }
        }
        return false;
    }
}
