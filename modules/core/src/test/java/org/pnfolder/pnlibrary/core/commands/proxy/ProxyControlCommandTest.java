package org.pnfolder.pnlibrary.core.commands.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeKind;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.commands.engine.CommandTreeRouter;
import org.pnfolder.pnlibrary.core.commands.engine.ExecutableRoute;
import org.pnfolder.pnlibrary.core.link.Link;

class ProxyControlCommandTest {
    private final CommandDefinition definition = new ProxyControlCommand(mock(PnLibrary.class), Link.OFF).definition();

    private static List<String> names(List<CommandNode> nodes) {
        List<String> names = new ArrayList<>();
        for (CommandNode node : nodes) {
            names.add(node.getName());
        }
        return names;
    }

    private CommandNode child(CommandNode parent, String name) {
        for (CommandNode node : parent.getChildren()) {
            if (node.getName().equals(name)) {
                return node;
            }
        }
        throw new AssertionError("no " + name);
    }

    @Test
    @DisplayName("the proxy has one pn command with other labels for the case that pn is taken")
    void theProxyHasOnePnCommandWithOtherLabelsForTheCaseThatPnIsTaken() {
        assertEquals("pnproxy", definition.getName());
        assertEquals(Collections.singleton("pnp"), definition.getAliases());
    }

    @Test
    @DisplayName("the proxy has the same words as Bukkit")
    void theProxyHasTheSameWordsAsBukkit() {
        List<String> words = names(definition.getRoot().getChildren());
        assertTrue(words.containsAll(Arrays.asList("status", "check", "updates", "update", "support", "bus", "tasks")), words.toString());
    }

    @Test
    @DisplayName("the update controls keep the catalog words under updates")
    void theUpdateControlsKeepTheCatalogWordsUnderUpdates() {
        CommandNode updates = child(definition.getRoot(), "updates");

        assertEquals(CommandNodeKind.LITERAL, updates.getKind());
        List<String> words = names(updates.getChildren());
        assertTrue(words.containsAll(Arrays.asList("list", "available", "history", "rollback")), words.toString());
        CommandNode update = child(definition.getRoot(), "update");
        assertTrue(names(update.getChildren()).containsAll(Arrays.asList("confirm", "rollback")));
    }

    @Test
    @DisplayName("pn alone tells a sender without the admin permission what it may use")
    void pnAloneTellsASenderWithoutTheAdminPermissionWhatItMayUse() {
        final List<String> sent = new ArrayList<>();
        CommandSender sender = new CommandSender() {
            @NotNull
            @Override
            public Pointers pointers() {
                return AudienceExtensions.senderPointers("Player", UUID.randomUUID(), permission -> permission.equals("pnlibrary.tasks"));
            }

            @Override
            public void sendMessage(@NotNull Component message) {
                sent.add(message.toString());
            }
        };

        ExecutableRoute route = (ExecutableRoute) new CommandTreeRouter().route(definition,
                new CommandContext(sender, Collections.emptyList(), "pn", ""));
        route.getNode().getExecution().execute(route.getContext()).toCompletableFuture().join();

        assertTrue(String.join(", ", sent).contains("/pn tasks"), sent.toString());
        assertTrue(!String.join(", ", sent).contains("/pn debug"), sent.toString());
    }
}
