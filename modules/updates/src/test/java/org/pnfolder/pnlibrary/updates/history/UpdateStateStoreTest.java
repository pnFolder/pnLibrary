package org.pnfolder.pnlibrary.updates.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.BlockedReason;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

class UpdateStateStoreTest {
    @TempDir
    Path directory;

    @Test
    void historicalStateNamesRemainReadableAfterApiCleanup() throws IOException {
        UpdateStateStore store = new UpdateStateStore(directory);
        Map<String, UpdateState> names = new LinkedHashMap<>();
        names.put("CURRENT", UpdateState.UP_TO_DATE);
        names.put("AVAILABLE", UpdateState.UPDATE_AVAILABLE);
        names.put("DOWNLOADED", UpdateState.UPDATE_STAGED);
        for (Map.Entry<String, UpdateState> entry : names.entrySet()) {
            UpdatePlanSnapshot snapshot = new UpdatePlanSnapshot(UUID.randomUUID(), 1, entry.getValue(), null,
                Collections.emptyList(), null);
            store.save(snapshot);
            Path path = directory.resolve("state.json");
            String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            Files.write(path, text.replace(entry.getValue().name(), entry.getKey()).getBytes(StandardCharsets.UTF_8));
            assertEquals(entry.getValue(), store.current().getState());
            assertEquals(snapshot.getId(), store.current().getId());
        }
    }

    @Test
    void persistsCurrentStateAtomicallyAndBoundsNewestFirstHistory() throws IOException {
        UpdateStateStore store = new UpdateStateStore(directory, 3, 1024 * 1024);
        List<UpdatePlanSnapshot> snapshots = new ArrayList<>();
        for (long revision = 1; revision <= 5; revision++) {
            UpdatePlanSnapshot snapshot = new UpdatePlanSnapshot(UUID.randomUUID(), revision, UpdateState.UP_TO_DATE,
                null, Collections.emptyList(), "revision-" + revision);
            store.save(snapshot);
            snapshots.add(snapshot);
        }

        assertEquals(5, store.current().getRevision());
        List<Long> revisions = new ArrayList<>();
        for (UpdatePlanSnapshot snapshot : store.history()) {
            revisions.add(snapshot.getRevision());
        }
        assertEquals(Arrays.asList(5L, 4L, 3L), revisions);
        assertFalse(Files.exists(directory.resolve("state.json.tmp")));
        try (Stream<Path> files = Files.list(directory.resolve("history"))) {
            assertEquals(3, files.count());
        }
        assertEquals(snapshots.get(snapshots.size() - 1).getId(), store.current().getId());
    }

    @Test
    void corruptStateIsQuarantinedInsteadOfBreakingStartup() throws IOException {
        Files.createDirectories(directory);
        Files.write(directory.resolve("state.json"), "not-json".getBytes(StandardCharsets.UTF_8));
        List<String> warnings = new ArrayList<>();

        UpdateStateStore store = new UpdateStateStore(directory, warnings::add);

        assertNull(store.current());
        assertEquals(1, warnings.size());
        try (Stream<Path> files = Files.list(directory)) {
            assertTrue(files.anyMatch(file -> file.getFileName().toString().startsWith("state.json.corrupt-")));
        }
    }

    @Test
    void restoresTheCompleteSelectedPlanAfterRestart() throws IOException {
        ProductId component = ProductId.of("pnlibrary");
        SemanticVersion version = SemanticVersion.parse("2.0.0");
        UpdatePlan plan = new UpdatePlan(1, Collections.singletonList(
            new ProductChange(component, SemanticVersion.parse("1.0.0"), version)),
            Collections.singletonList(new ProductRelease(component, version, UpdateChannel.STABLE,
                new ApiVersionRange(1, 1), 1, Collections.emptyList(), null, Collections.emptyList(),
                Collections.emptyList(), Instant.parse("2026-09-29T12:00:00Z"), null, "")));
        UpdatePlanSnapshot snapshot = new UpdatePlanSnapshot(UUID.randomUUID(), 9, UpdateState.UPDATE_AVAILABLE, plan,
            Collections.emptyList(), null);

        new UpdateStateStore(directory).save(snapshot);
        UpdatePlanSnapshot restored = new UpdateStateStore(directory).current();

        assertEquals("pnlibrary", restored.getPlan().getChanges().get(0).getProduct().getValue());
        assertEquals("2.0.0", restored.getPlan().getSelected().get(0).getVersion().toString());
        assertEquals(Instant.parse("2026-09-29T12:00:00Z"), restored.getPlan().getSelected().get(0).getPublishedAt());
    }

    @Test
    void restoresStructuredBlockerReasonsAfterRestart() throws IOException {
        BlockedReason blocker = new BlockedReason.NoCompatibleRelease(ProductId.of("acceptance"), 2);
        UpdatePlanSnapshot snapshot = new UpdatePlanSnapshot(UUID.randomUUID(), 10, UpdateState.BLOCKED, null,
            Collections.singletonList(blocker), null);

        new UpdateStateStore(directory).save(snapshot);
        UpdatePlanSnapshot restored = new UpdateStateStore(directory).current();

        assertEquals(Collections.singletonList(blocker), restored.getBlockers());
    }
}
