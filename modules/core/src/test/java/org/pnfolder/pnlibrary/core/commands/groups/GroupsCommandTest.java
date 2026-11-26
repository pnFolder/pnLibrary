package org.pnfolder.pnlibrary.core.commands.groups;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.core.commands.engine.CommandRunner;
import org.pnfolder.pnlibrary.core.commands.engine.RecordingSender;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.link.ServerGroup;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;

class GroupsCommandTest {
    @TempDir
    Path directory;

    private Link link;
    private CommandDefinition definition;
    private final RecordingSender sender = new RecordingSender();

    private String run(String... words) {
        if (link == null) {
            link = new Link(null, directory);
            definition = new GroupsCommand(link, () -> Collections.<NetworkServer>emptyList()).definition();
        }
        return CommandRunner.run(definition, sender, "groups", words);
    }

    @Test
    @DisplayName("a group is created once and a bad name is refused")
    void aGroupIsCreatedOnceAndABadNameIsRefused() {
        assertTrue(run("create", "grief").contains("создана"));
        assertTrue(run("create", "grief").contains("уже есть"));
        StringBuilder long40 = new StringBuilder();
        for (int index = 0; index < 40; index++) {
            long40.append('x');
        }
        assertTrue(run("create", long40.toString()).contains("Не подходит имя"));
        assertNotNull(link.groups().get("GRIEF"));
    }

    @Test
    @DisplayName("join, require and a per-server difference are saved in the group")
    void joinRequireAndAPerServerDifferenceAreSavedInTheGroup() {
        run("create", "grief");

        run("join", "grief", "grief-1");
        run("require", "grief", "Shop");
        run("skip", "grief", "grief-1", "Shop");
        run("extra", "grief", "grief-1", "Quests");
        ServerGroup group = link.groups().get("grief");

        assertEquals(new HashSet<String>(Arrays.asList("grief-1")), group.getServers());
        assertEquals(new HashSet<String>(Arrays.asList("Shop")), group.getPlugins());
        assertEquals(new HashSet<String>(Arrays.asList("Quests")), group.wanted("grief-1"));
        assertTrue(group.customized("grief-1"));
    }

    @Test
    @DisplayName("reset returns a server to what the rest have and leave takes it out")
    void resetReturnsAServerToWhatTheRestHaveAndLeaveTakesItOut() {
        run("create", "grief");
        run("join", "grief", "grief-1");
        run("require", "grief", "Shop");
        run("skip", "grief", "grief-1", "Shop");

        run("reset", "grief", "grief-1");
        assertEquals(new HashSet<String>(Arrays.asList("Shop")), link.groups().get("grief").wanted("grief-1"));

        run("leave", "grief", "grief-1");
        assertTrue(link.groups().get("grief").getServers().isEmpty());
    }

    @Test
    @DisplayName("drop removes a plugin from the group")
    void dropRemovesAPluginFromTheGroup() {
        run("create", "grief");
        run("require", "grief", "Shop");

        run("drop", "grief", "shop");

        assertTrue(link.groups().get("grief").getPlugins().isEmpty());
    }

    @Test
    @DisplayName("delete removes the group and an unknown group is reported")
    void deleteRemovesTheGroupAndAnUnknownGroupIsReported() {
        run("create", "grief");

        assertTrue(run("delete", "grief").contains("удалена"));
        assertNull(link.groups().get("grief"));
        assertTrue(run("delete", "grief").contains("Нет группы"));
        assertTrue(run("join", "ghost", "s1").contains("Нет группы"));
    }

    @Test
    @DisplayName("info on an unknown group does not crash")
    void infoOnAnUnknownGroupDoesNotCrash() {
        assertFalse(run("info", "ghost").trim().isEmpty());
    }
}
