package org.pnfolder.pnlibrary.core.commands.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;
import org.pnfolder.pnlibrary.api.network.NetworkProxy;
import org.pnfolder.pnlibrary.core.commands.engine.CommandRunner;
import org.pnfolder.pnlibrary.core.commands.engine.RecordingSender;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.link.report.LinkPlugin;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;

class ServersCommandTest {
    private static LinkReport report(int players, LinkPlugin... plugins) {
        return new LinkReport("Paper 1.21", "0.1.0", players, Arrays.asList(plugins), Instant.now());
    }

    private final List<NetworkServer> servers = Arrays.asList(
            new NetworkServer("lobby", true, report(3, new LinkPlugin("shop", "1.0", "1.1", "UPDATE_AVAILABLE"))),
            new NetworkServer("games", false, report(0)));
    private final RecordingSender sender = new RecordingSender();

    private String run(final List<NetworkServer> shown, String... words) {
        CommandDefinition definition = new ServersCommand(Link.OFF, () -> shown).definition();
        return CommandRunner.run(definition, sender, "servers", words);
    }

    @Test
    @DisplayName("the list shows each server with its state, players and the number of updates")
    void theListShowsEachServerWithItsStatePlayersAndTheNumberOfUpdates() {
        String text = run(servers);

        assertTrue(text.contains("lobby"), text);
        assertTrue(text.contains("на связи"), text);
        assertTrue(text.contains("games"), text);
        assertTrue(text.contains("нет связи"), text);
        assertTrue(text.contains("3 игрока"), text);
        assertTrue(text.contains("обновлений: 1"), text);
    }

    @Test
    @DisplayName("an empty list explains what to do about the key")
    void anEmptyListExplainsWhatToDoAboutTheKey() {
        assertTrue(run(Collections.emptyList()).contains("link.key"));
    }

    @Test
    @DisplayName("one server shows its plugins with versions and the update state")
    void oneServerShowsItsPluginsWithVersionsAndTheUpdateState() {
        String text = run(servers, "lobby");

        assertTrue(text.contains("shop"), text);
        assertTrue(text.contains("v1.0 >> v1.1"), text);
    }

    @Test
    @DisplayName("the name is matched without regard to case and an unknown server is refused")
    void theNameIsMatchedWithoutRegardToCaseAndAnUnknownServerIsRefused() {
        assertTrue(run(servers, "LOBBY").contains("pnLibrary"));
        assertTrue(run(servers, "ghost").contains("не присылал отчёт"));
    }

    @Test
    @DisplayName("a request without a link key says why it was not sent")
    void aRequestWithoutALinkKeySaysWhyItWasNotSent() {
        String text = run(servers, "lobby", "check");

        assertTrue(text.contains("нет файла link.key"), text);
    }

    @Test
    @DisplayName("the permission is the same for the list and its words")
    void thePermissionIsTheSameForTheListAndItsWords() {
        CommandDefinition definition = new ServersCommand(Link.OFF, () -> servers).definition();

        assertEquals("servers", definition.getName());
        List<String> names = new ArrayList<>();
        for (CommandNode child : definition.getRoot().getChildren().get(0).getChildren()) {
            names.add(child.getName());
        }
        assertTrue(names.containsAll(Arrays.asList("check", "update")));
        assertFalse(names.contains("auto"));
    }

    private String runOnProxy(NetworkProxy proxy, String... words) {
        CommandDefinition definition = new ServersCommand(Link.OFF, () -> servers, proxy).definition();
        return CommandRunner.run(definition, sender, "servers", words);
    }

    @Test
    @DisplayName("add and remove change the servers of the proxy and say what happened")
    void addAndRemoveChangeTheServersOfTheProxyAndSayWhatHappened() {
        NetworkProxy proxy = org.mockito.Mockito.mock(NetworkProxy.class);
        org.pnfolder.pnlibrary.api.network.NetworkServer arena = org.mockito.Mockito.mock(org.pnfolder.pnlibrary.api.network.NetworkServer.class);
        org.mockito.Mockito.when(arena.getName()).thenReturn("arena");
        org.mockito.Mockito.when(proxy.addServer(org.mockito.ArgumentMatchers.eq("arena"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(arena);
        org.mockito.Mockito.when(proxy.server("arena")).thenReturn(arena);
        org.mockito.Mockito.when(proxy.removeServer(arena)).thenReturn(true);

        assertTrue(runOnProxy(proxy, "add", "arena", "127.0.0.1:25570").contains("добавлен"));
        assertTrue(runOnProxy(proxy, "remove", "arena").contains("удалён"));
    }

    @Test
    @DisplayName("a bad address, a taken name, an unknown server and a server of the proxy's own configuration get a clear answer")
    void refusalsAreExplained() {
        NetworkProxy proxy = org.mockito.Mockito.mock(NetworkProxy.class);
        org.pnfolder.pnlibrary.api.network.NetworkServer own = org.mockito.Mockito.mock(org.pnfolder.pnlibrary.api.network.NetworkServer.class);
        org.mockito.Mockito.when(own.getName()).thenReturn("lobby");
        org.mockito.Mockito.when(proxy.server("lobby")).thenReturn(own);
        org.mockito.Mockito.when(proxy.addServer(org.mockito.ArgumentMatchers.eq("lobby"), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("A server named lobby already exists"));

        assertTrue(runOnProxy(proxy, "add", "x", "not-an-address").contains("host:port"));
        assertTrue(runOnProxy(proxy, "add", "lobby", "127.0.0.1:25570").contains("already exists"));
        assertTrue(runOnProxy(proxy, "remove", "ghost").contains("нет"));
        assertTrue(runOnProxy(proxy, "remove", "lobby").contains("настройка прокси"));
    }
}
