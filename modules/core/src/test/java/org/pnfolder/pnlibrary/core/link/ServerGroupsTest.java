package org.pnfolder.pnlibrary.core.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.core.link.report.LinkPlugin;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;

class ServerGroupsTest {
    private static LinkReport report(LinkPlugin... plugins) {
        return new LinkReport("Paper", "0.0.1", 0, Arrays.asList(plugins), Instant.now());
    }

    private static Set<String> setOf(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }

    private static Map<String, ServerGroupState> byServer(List<ServerGroupState> states) {
        Map<String, ServerGroupState> map = new HashMap<>();
        for (ServerGroupState state : states) {
            map.put(state.getServer(), state);
        }
        return map;
    }

    private static List<String> names(List<ServerGroup> groups) {
        List<String> names = new ArrayList<>();
        for (ServerGroup group : groups) {
            names.add(group.getName());
        }
        return names;
    }

    @Test
    void groupsAreKeptInTheFileAndEditedByName(@TempDir Path folder) {
        ServerGroups groups = new ServerGroups(() -> folder.resolve(ServerGroups.FILE));

        assertTrue(groups.create("grief"));
        assertFalse(groups.create("GRIEF"));
        assertTrue(groups.create("create"));
        assertFalse(groups.create("two words"));
        assertTrue(groups.delete("create"));
        assertTrue(groups.edit("Grief", it -> it.addServer("g1").addPlugin("shop")));

        ServerGroup again = new ServerGroups(() -> folder.resolve(ServerGroups.FILE)).get("grief");
        assertEquals("grief", again.getName());
        assertEquals(setOf("g1"), again.getServers());
        assertEquals(setOf("shop"), again.getPlugins());
        assertTrue(groups.delete("grief"));
        assertNull(groups.get("grief"));
        assertFalse(groups.edit("grief", it -> it));
    }

    @Test
    void oneServerOfAGroupCanSkipAPluginOrCarryOneMore(@TempDir Path folder) {
        ServerGroups groups = new ServerGroups(() -> folder.resolve(ServerGroups.FILE));
        groups.create("lobby");
        groups.create("event");
        groups.edit("lobby", it -> new ServerGroup(it.getName(), setOf("l1", "l2"), setOf("shop", "chat")));
        groups.edit("event", it -> new ServerGroup(it.getName(), setOf("l1"), setOf("quiz")));
        groups.edit("lobby", it -> it.with("l2", "chat", false).with("l2", "parkour", true));

        ServerGroup lobby = new ServerGroups(() -> folder.resolve(ServerGroups.FILE)).get("lobby");
        assertEquals(setOf("shop", "chat"), lobby.wanted("l1"));
        assertEquals(setOf("shop", "parkour"), lobby.wanted("l2"));
        assertTrue(lobby.customized("l2"));
        assertFalse(lobby.customized("l1"));
        assertEquals(Arrays.asList("event", "lobby"), names(groups.of("L1")));

        groups.edit("lobby", it -> it.with("l2", "chat", true).reset("l2"));
        assertEquals(setOf("shop", "chat"), groups.get("lobby").wanted("l2"));
        assertFalse(groups.get("lobby").customized("l2"));
    }

    @Test
    void theProxyIsAMemberUnderANameNoServerHas() {
        ServerGroup group = new ServerGroup("all", setOf(ServerGroups.PROXY, "proxy"), setOf("shop"));
        List<NetworkServer> servers = Arrays.asList(
            new NetworkServer(ServerGroups.PROXY, true, report(new LinkPlugin("shop", "1", null, "UP_TO_DATE"))),
            new NetworkServer("proxy", true, report()));

        Map<String, ServerGroupState> states = byServer(new ServerGroups(() -> null).compare(group, servers));

        assertTrue(states.get(ServerGroups.PROXY).getOk());
        assertEquals(Collections.singletonList("shop"), states.get("proxy").getMissing());
        assertEquals("Прокси", ServerGroups.label(ServerGroups.PROXY));
        assertEquals("proxy", ServerGroups.label("proxy"));
    }

    @Test
    void aSkippedPluginIsNotMissingOnThatServer() {
        ServerGroup group = new ServerGroup("lobby", setOf("l1"), setOf("shop", "chat")).with("l1", "chat", false);
        List<NetworkServer> servers = Collections.singletonList(
            new NetworkServer("l1", true, report(new LinkPlugin("shop", "1", null, "UP_TO_DATE"))));

        ServerGroupState state = new ServerGroups(() -> null).compare(group, servers).get(0);

        assertTrue(state.getOk());
        assertTrue(state.getCustomized());
    }

    @Test
    void aServerIsComparedWithWhatTheGroupAsksFor(@TempDir Path folder) {
        ServerGroups groups = new ServerGroups(() -> folder.resolve(ServerGroups.FILE));
        ServerGroup group = new ServerGroup("grief", setOf("g1", "g2", "g3"), setOf("shop", "warps"));
        List<NetworkServer> servers = Arrays.asList(
            new NetworkServer("g1", true, report(new LinkPlugin("shop", "1", null, "UP_TO_DATE"),
                new LinkPlugin("warps", "1", null, "UP_TO_DATE"))),
            new NetworkServer("g2", true, report(new LinkPlugin("shop", "1", "2", "UPDATE_AVAILABLE"))));

        Map<String, ServerGroupState> states = byServer(groups.compare(group, servers));

        assertTrue(states.get("g1").getOk());
        assertEquals(Collections.singletonList("warps"), states.get("g2").getMissing());
        assertEquals(Collections.singletonList("shop"), states.get("g2").getOutdated());
        assertNull(states.get("g3").getKnown());
        assertFalse(states.get("g3").getOk());
    }
}
