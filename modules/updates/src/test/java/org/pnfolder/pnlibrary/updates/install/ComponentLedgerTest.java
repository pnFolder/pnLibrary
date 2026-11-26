package org.pnfolder.pnlibrary.updates.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.updates.history.UpdateEvent;
import org.pnfolder.pnlibrary.updates.history.UpdateEventJournal;

class ComponentLedgerTest {
    @TempDir
    Path directory;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);
    private final List<String> warnings = new ArrayList<>();
    private UpdateEventJournal journal;
    private ComponentLedger ledger;

    @BeforeEach
    void setUp() throws IOException {
        journal = new UpdateEventJournal(directory.resolve("events"), clock);
        ledger = new ComponentLedger(directory.resolve("components.json"), journal, warnings::add);
    }

    private static ComponentLedger.Component component(String version) {
        return component(version, "stable", false);
    }

    private static ComponentLedger.Component component(String version, String channel, boolean automatic) {
        return new ComponentLedger.Component(version, channel, automatic);
    }

    private static Map<String, ComponentLedger.Component> components(Object... pairs) {
        Map<String, ComponentLedger.Component> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            map.put((String) pairs[index], (ComponentLedger.Component) pairs[index + 1]);
        }
        return map;
    }

    private Set<Map.Entry<String, String>> history() throws IOException {
        Set<Map.Entry<String, String>> recorded = new HashSet<>();
        for (UpdateEvent event : journal.read(1, 100, null)) {
            recorded.add(new AbstractMap.SimpleEntry<>(event.getOperation(), event.getChanges().get(0).getProduct()));
        }
        return recorded;
    }

    @Test
    @DisplayName("the first observation is only a baseline and writes no history")
    void theFirstObservationIsOnlyABaselineAndWritesNoHistory() throws IOException {
        ledger.observe(components("pnlibrary", component("2.0.0"), "currency", component("1.0.0")));
        assertEquals(0, journal.count());
        assertTrue(Files.isRegularFile(directory.resolve("components.json")));
    }

    @Test
    @DisplayName("new, removed and manually changed components are recorded")
    void newRemovedAndManuallyChangedComponentsAreRecorded() throws IOException {
        ledger.observe(components("pnlibrary", component("2.0.0"), "currency", component("1.0.0")));
        ledger.observe(components("pnlibrary", component("2.1.0"), "shop", component("0.5.0")));

        Set<Map.Entry<String, String>> expected = new HashSet<>(Arrays.<Map.Entry<String, String>>asList(
            new AbstractMap.SimpleEntry<>(ComponentOperation.ADDED, "shop"),
            new AbstractMap.SimpleEntry<>(ComponentOperation.REMOVED, "currency"),
            new AbstractMap.SimpleEntry<>(ComponentOperation.VERSION_CHANGED, "pnlibrary")));
        assertEquals(expected, history());
        List<UpdateEvent> events = journal.read(1, 10, "pnlibrary");
        assertEquals(1, events.size());
        assertEquals("2.0.0", events.get(0).getChanges().get(0).getFrom());
        assertEquals("2.1.0", events.get(0).getChanges().get(0).getTo());
    }

    @Test
    @DisplayName("a version change already verified by a restart health check is not reported twice")
    void aVersionChangeAlreadyVerifiedByARestartHealthCheckIsNotReportedTwice() throws IOException {
        ledger.observe(components("pnlibrary", component("2.0.0")));
        ledger.observe(components("pnlibrary", component("2.1.0")), Collections.singleton("pnlibrary"));
        assertEquals(0, journal.count());
    }

    @Test
    @DisplayName("channel and automatic download changes are described in the event message")
    void channelAndAutomaticDownloadChangesAreDescribedInTheEventMessage() throws IOException {
        ledger.observe(components("pnlibrary", component("2.0.0", "stable", false)));
        ledger.observe(components("pnlibrary", component("2.0.0", "beta", true)));

        List<UpdateEvent> events = journal.read();
        assertEquals(1, events.size());
        assertEquals(ComponentOperation.SETTINGS_CHANGED, events.get(0).getOperation());
        assertEquals("канал: stable → beta; автозагрузка: выкл → вкл", events.get(0).getMessage());
    }

    @Test
    @DisplayName("an unchanged start records nothing and the state survives reopening")
    void anUnchangedStartRecordsNothingAndTheStateSurvivesReopening() throws IOException {
        ledger.observe(components("pnlibrary", component("2.0.0")));
        ComponentLedger reopened = new ComponentLedger(directory.resolve("components.json"), journal, warnings::add);
        reopened.observe(components("pnlibrary", component("2.0.0")));
        assertEquals(0, journal.count());

        reopened.observe(components("pnlibrary", component("2.0.0"), "shop", component("1.0.0")));
        assertEquals(1, journal.count());
    }

    @Test
    @DisplayName("an unreadable ledger is treated as a new baseline instead of failing")
    void anUnreadableLedgerIsTreatedAsANewBaselineInsteadOfFailing() throws IOException {
        Files.write(directory.resolve("components.json"), "{ not json".getBytes(StandardCharsets.UTF_8));
        ledger.observe(components("pnlibrary", component("2.0.0")));
        assertEquals(0, journal.count());
        assertEquals(1, warnings.size());
    }
}
