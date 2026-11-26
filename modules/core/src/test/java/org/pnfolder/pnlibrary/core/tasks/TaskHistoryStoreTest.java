package org.pnfolder.pnlibrary.core.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;

class TaskHistoryStoreTest {
    @TempDir
    Path directory;

    private TaskSnapshot task(String id, String failure) {
        return TaskSnapshot.builder(new TaskId(id), "Shop", TaskExecution.Kind.ASYNC,
                        failure == null ? TaskStatus.COMPLETED : TaskStatus.FAILED, Instant.parse("2026-10-29T10:00:00Z"))
                .name("Save\tplayers")
                .tags(new HashSet<>(Arrays.asList("a", "b")))
                .runCount(3)
                .skippedCount(1)
                .lastStartedAt(Instant.parse("2026-10-29T10:00:01Z"))
                .lastCompletedAt(Instant.parse("2026-10-29T10:00:02Z"))
                .lastFailure(failure)
                .description("Writes players")
                .source("org.example.Saver")
                .interval(Duration.ofMinutes(5))
                .lastDuration(Duration.ofNanos(1_500_000))
                .cancellable(false)
                .runnableNow(true)
                .hidden(true)
                .build();
    }

    @Test
    void savedTasksComeBackUnchangedIncludingTabsAndLineBreaks() {
        TaskHistoryStore store = new TaskHistoryStore(directory.resolve("history.tsv"));
        List<TaskSnapshot> saved = Arrays.asList(task("one", null), task("two", "IllegalStateException: boom\n\tat x\\y"));

        store.save(saved);

        assertEquals(saved, store.load());
    }

    @Test
    void aMissingOrDamagedFileGivesTheReadableLinesOnly() throws IOException {
        Path file = directory.resolve("history.tsv");
        TaskHistoryStore store = new TaskHistoryStore(file);
        assertEquals(Collections.emptyList(), store.load());

        store.save(Arrays.asList(task("one", null)));
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        Files.write(file, (text + "garbage line\n").getBytes(StandardCharsets.UTF_8));

        assertEquals(Arrays.asList(task("one", null)), store.load());
    }

    @Test
    void aLineOfTheFirstFormatStillLoadsWithDefaultNewFields() throws IOException {
        Path file = directory.resolve("history.tsv");
        List<String> fields = new ArrayList<>(Arrays.asList(
                "id1", "Old", "", "Shop", "ASYNC", "COMPLETED", "", "2026-10-29T10:00:00Z", "", "2", "0", "", "", "", "", ""));
        Files.write(file, ("#pnlibrary-task-history 1\n" + String.join("\t", fields) + "\n").getBytes(StandardCharsets.UTF_8));

        TaskSnapshot loaded = new TaskHistoryStore(file).load().get(0);

        assertEquals("Old", loaded.getName());
        assertTrue(loaded.getCancellable());
        assertNull(loaded.getSource());
    }
}
