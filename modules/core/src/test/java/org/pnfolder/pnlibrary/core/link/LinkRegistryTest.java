package org.pnfolder.pnlibrary.core.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.link.report.LinkPlugin;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;

class LinkRegistryTest {
    private static LinkReport report(int players) {
        return new LinkReport("paper", "1.0", players, Collections.emptyList(), Instant.EPOCH);
    }

    @Test
    void aNewerReportReplacesTheOldOneAndLookupIgnoresCase() {
        LinkRegistry registry = new LinkRegistry();
        registry.accept("Lobby", report(1));
        registry.accept("Lobby", report(5));

        assertEquals(5, registry.get("lobby").getReport().getPlayers());
        assertEquals(1, registry.all().size());
        assertNull(registry.get("other"));
    }

    @Test
    void serversAreListedByNameAndCanBeCleared() {
        LinkRegistry registry = new LinkRegistry();
        registry.accept("beta", report(0));
        registry.accept("Alpha", report(0));

        List<String> names = new ArrayList<>();
        for (LinkEntry entry : registry.all()) {
            names.add(entry.getName());
        }
        assertEquals(Arrays.asList("Alpha", "beta"), names);
        registry.clear();
        assertEquals(0, registry.all().size());
    }
}
