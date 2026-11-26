package org.pnfolder.pnlibrary.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.pnfolder.pnlibrary.common.lang.Pair;
import org.junit.jupiter.api.Test;

class StatsSnapshotTest {
    private final StatsSnapshot snapshot = new StatsSnapshot(1_000, 4, 1, 120, 3, 9_000, 7, 12, 5,
        Arrays.asList(new Pair<>("pnmarket", 3), new Pair<>("pnclans", 2)));

    @Test
    void aStoredSnapshotReadsBackTheSame() {
        assertEquals(snapshot, StatsSnapshot.fromDetails(1_000, snapshot.toDetails()));
    }

    @Test
    void aRecordWithAMissingNumberIsIgnored() {
        Map<String, String> details = new HashMap<>(snapshot.toDetails());
        details.remove("taskRuns");
        assertNull(StatsSnapshot.fromDetails(1_000, details));
    }

    @Test
    void storedDetailsHoldOnlyNumbersAndPluginIds() {
        Map<String, String> details = snapshot.toDetails();
        Set<String> nonNumbers = new HashSet<>();
        for (Map.Entry<String, String> entry : details.entrySet()) {
            if (!entry.getValue().matches("\\d+")) {
                nonNumbers.add(entry.getKey());
            }
        }
        assertEquals(new HashSet<>(Arrays.asList("errorSources")), nonNumbers);
        assertTrue(details.get("errorSources").matches("[a-z0-9_]+:\\d+(,[a-z0-9_]+:\\d+)*"));
    }
}
