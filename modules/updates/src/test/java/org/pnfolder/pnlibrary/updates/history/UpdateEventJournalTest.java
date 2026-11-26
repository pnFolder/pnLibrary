package org.pnfolder.pnlibrary.updates.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

class UpdateEventJournalTest {
    @TempDir
    Path directory;

    private final Instant instant = Instant.parse("2026-10-17T12:00:00Z");
    private final Clock clock = Clock.fixed(instant, ZoneOffset.UTC);

    @Test
    void initiatorAndOperationSurviveRestartWithoutBeingInferredFromState() throws IOException {
        UpdateActor actor = new UpdateActor(UpdateActor.Kind.PLAYER, UUID.randomUUID().toString(), "Admin");
        new UpdateEventJournal(directory, clock).record(snapshot(1, "pnlibrary"), actor, "STAGE");
        UpdateEvent restored = single(new UpdateEventJournal(directory).read());
        assertEquals(actor, restored.getActor());
        assertEquals("STAGE", restored.getOperation());
        assertNotEquals(UpdateActor.SYSTEM, restored.getActor());
    }

    @Test
    void observationsSurviveReopeningWithExactTimeAndVersionTransitions() throws IOException {
        UpdateEventJournal journal = new UpdateEventJournal(directory, clock);
        UpdatePlanSnapshot snapshot = snapshot(1, "pnlibrary");
        UpdateEvent event = journal.record(snapshot);
        UpdateEvent restored = single(new UpdateEventJournal(directory).read());
        assertEquals(event, restored);
        assertEquals(instant, restored.getOccurredAt());
        assertEquals(snapshot.getId(), restored.getPlanId());
        assertEquals(Collections.singletonList(new UpdateEventChange("pnlibrary", "1.0.0", "1.1.0")),
            restored.getChanges());
        assertEquals(1, files());
    }

    @Test
    void pagesAreNewestFirstAndProductFiltersApplyBeforePagination() throws IOException {
        UpdateEventJournal journal = new UpdateEventJournal(directory, clock);
        journal.record(snapshot(1, "pnlibrary"));
        journal.record(snapshot(2, "currency"));
        journal.record(snapshot(3, "pnlibrary"));
        assertEquals(Arrays.asList(3L, 2L), revisions(journal.read(1, 2, null)));
        assertEquals(Collections.singletonList(1L), revisions(journal.read(2, 2, null)));
        assertEquals(Arrays.asList(3L, 1L), revisions(journal.read(1, 10, "PNLIBRARY")));
        assertTrue(journal.read(Integer.MAX_VALUE, 10, null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> journal.read(0, 10, null));
        assertThrows(IllegalArgumentException.class, () -> journal.read(1, 101, null));
    }

    @Test
    void corruptEntriesDoNotHideValidHistoryAndTemporaryFilesAreIgnored() throws IOException {
        List<String> warnings = new ArrayList<>();
        UpdateEventJournal journal = new UpdateEventJournal(directory, clock, warnings::add);
        journal.record(snapshot(1, "pnlibrary"));
        write(directory.resolve("broken.json"), "not json");
        write(directory.resolve("unfinished.tmp"), "partial");
        assertEquals(1, journal.read().size());
        assertEquals(1, warnings.size());
        assertTrue(Files.exists(directory.resolve("broken.json")));
    }

    @Test
    void anObservationIdenticalToThePreviousOneIsNotStoredAgain() throws IOException {
        UpdateEventJournal journal = new UpdateEventJournal(directory, clock);
        journal.record(snapshot(1, "pnlibrary"));
        journal.record(snapshot(2, "pnlibrary"));
        assertEquals(1, journal.count());

        journal.record(snapshot(3, "currency"));
        journal.record(snapshot(4, "pnlibrary"));
        assertEquals(3, journal.count());
        assertEquals(2, journal.count("pnlibrary"));
    }

    @Test
    void duplicateSuppressionAlsoAppliesRightAfterReopeningTheJournal() throws IOException {
        new UpdateEventJournal(directory, clock).record(snapshot(1, "pnlibrary"));
        new UpdateEventJournal(directory, clock).record(snapshot(2, "pnlibrary"));
        assertEquals(1, new UpdateEventJournal(directory, clock).count());
    }

    @Test
    void onlyTheNewestEventsWithinTheMaximumAgeAreKept() throws IOException {
        UpdateEventJournal journal = new UpdateEventJournal(directory, clock, message -> {
        }, Duration.ofDays(30), 2);
        journal.record(snapshot(1, "a"));
        journal.record(snapshot(2, "b"));
        journal.record(snapshot(3, "c"));
        journal.prune();
        assertEquals(2, journal.count());

        List<Path> files;
        try (Stream<Path> stream = Files.list(directory)) {
            files = stream.collect(Collectors.toList());
        }
        Files.setLastModifiedTime(files.get(0), FileTime.from(instant.minusSeconds(40L * 86_400)));
        assertEquals(1, journal.prune());
        assertEquals(1, journal.count());
    }

    @Test
    void checksThatFoundNothingAreKeptOnDiskButLeftOutOfTheHistoryPeopleRead() throws IOException {
        UpdateEventJournal journal = new UpdateEventJournal(directory, clock);
        UpdatePlanSnapshot quiet = new UpdatePlanSnapshot(UUID.randomUUID(), 1, UpdateState.UP_TO_DATE, null,
            Collections.emptyList(), null);
        journal.record(quiet, UpdateActor.SYSTEM, "CHECK");
        journal.record(snapshot(2, "pnlibrary"), UpdateActor.SYSTEM, "CHECK");
        assertEquals(Collections.singletonList(2L), revisions(journal.read()));
        assertEquals(1, journal.count());
        assertEquals(2, files());
    }

    private long files() throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.count();
        }
    }

    private static UpdateEvent single(List<UpdateEvent> events) {
        assertEquals(1, events.size());
        return events.get(0);
    }

    private static List<Long> revisions(List<UpdateEvent> events) {
        List<Long> result = new ArrayList<>();
        for (UpdateEvent event : events) {
            result.add(event.getRevision());
        }
        return result;
    }

    private static void write(Path file, String text) throws IOException {
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static UpdatePlanSnapshot snapshot(long revision, String product) {
        UpdatePlan plan = new UpdatePlan(8, Collections.singletonList(new ProductChange(ProductId.of(product),
            SemanticVersion.parse("1.0.0"), SemanticVersion.parse("1.1.0"))), Collections.emptyList());
        return new UpdatePlanSnapshot(UUID.randomUUID(), revision, UpdateState.UPDATE_AVAILABLE, plan,
            Collections.emptyList(), null);
    }
}
