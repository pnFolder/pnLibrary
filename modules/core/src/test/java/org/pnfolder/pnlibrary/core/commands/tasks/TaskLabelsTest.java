package org.pnfolder.pnlibrary.core.commands.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;

class TaskLabelsTest {
    @Test
    @DisplayName("a span is written in the largest fitting unit and drops empty remainders")
    void aSpanIsWrittenInTheLargestFittingUnitAndDropsEmptyRemainders() {
        assertEquals("250 мс", TaskLabels.span(Duration.ofMillis(250)));
        assertEquals("5 с", TaskLabels.span(Duration.ofSeconds(5)));
        assertEquals("3 мин", TaskLabels.span(Duration.ofMinutes(3)));
        assertEquals("2 ч", TaskLabels.span(Duration.ofHours(2)));
        assertEquals("2 ч 30 мин", TaskLabels.span(Duration.ofMinutes(150)));
        assertEquals("1 ч 10 мин", TaskLabels.span(Duration.ofMinutes(70)));
        assertEquals("3 д", TaskLabels.span(Duration.ofDays(3)));
        assertEquals("1 д 10 ч", TaskLabels.span(Duration.ofHours(34)));
    }

    @Test
    @DisplayName("ago says just now, how long ago, or how long until")
    void agoSaysJustNowHowLongAgoOrHowLongUntil() {
        assertEquals("только что", TaskLabels.ago(Instant.now().minusSeconds(10)));
        assertEquals("только что", TaskLabels.ago(Instant.now().plusSeconds(10)));
        assertTrue(TaskLabels.ago(Instant.now().minus(Duration.ofMinutes(5))).endsWith("назад"));
        assertTrue(TaskLabels.ago(Instant.now().plus(Duration.ofHours(2).plusSeconds(30))).startsWith("через"));
    }

    @Test
    @DisplayName("every status has its own words")
    void everyStatusHasItsOwnWords() {
        Set<String> words = new HashSet<String>();
        for (TaskStatus status : TaskStatus.values()) {
            words.add(TaskLabels.label(status));
        }
        assertEquals(TaskStatus.values().length, words.size());
    }

    @Test
    @DisplayName("a lag is mentioned only when it is noticeable")
    void aLagIsMentionedOnlyWhenItIsNoticeable() {
        assertNull(TaskLabels.lag(task("task", null, TaskStatus.SCHEDULED, Instant.now(), null, true, null, Duration.ofMillis(400))));
        String lag = TaskLabels.lag(task("task", null, TaskStatus.SCHEDULED, Instant.now(), null, true, Duration.ofSeconds(1),
                Duration.ofSeconds(3)));
        assertTrue(lag.contains("наибольшее 3 с"));
    }

    @Test
    @DisplayName("stop and run answers say why not")
    void stopAndRunAnswersSayWhyNot() {
        assertEquals("да", TaskLabels.stopAnswer(plain("task", TaskStatus.SCHEDULED, true)));
        assertTrue(TaskLabels.stopAnswer(plain("task", TaskStatus.SCHEDULED, false)).contains("плагин запретил"));
        assertTrue(TaskLabels.runAnswer(plain("task", TaskStatus.RUNNING, true)).contains("выполняется"));
        assertFalse(TaskLabels.runAnswer(plain("task", TaskStatus.COMPLETED, true)).startsWith("да"));
    }

    @Test
    @DisplayName("the title falls back from name to key to a short id")
    void theTitleFallsBackFromNameToKeyToAShortId() {
        assertEquals("cleanup", TaskLabels.title(plain("cleanup", TaskStatus.SCHEDULED, true)));
        assertEquals("k1", TaskLabels.title(task(null, "k1", TaskStatus.SCHEDULED, Instant.now(), null, true, null, null)));
        assertEquals(TaskLabels.SHORT_ID,
                TaskLabels.title(task(null, null, TaskStatus.SCHEDULED, Instant.now(), null, true, null, null)).length());
    }

    @Test
    @DisplayName("an older finished task falls out of today and the week but active ones always stay")
    void anOlderFinishedTaskFallsOutOfTodayAndTheWeekButActiveOnesAlwaysStay() {
        Instant monthAgo = Instant.now().minus(Duration.ofDays(30));
        TaskSnapshot old = task("task", null, TaskStatus.COMPLETED, monthAgo, monthAgo, true, null, null);
        TaskSnapshot running = task("task", null, TaskStatus.RUNNING, monthAgo, null, true, null, null);

        assertFalse(TaskPeriod.TODAY.keep(old));
        assertFalse(TaskPeriod.WEEK.keep(old));
        assertTrue(TaskPeriod.OLDER.keep(old));
        assertTrue(TaskPeriod.ANY.keep(old));
        assertTrue(TaskPeriod.TODAY.keep(running));
        assertFalse(TaskPeriod.OLDER.keep(running));
    }

    private static TaskSnapshot plain(String name, TaskStatus status, boolean cancellable) {
        return task(name, null, status, Instant.now(), null, cancellable, null, null);
    }

    private static TaskSnapshot task(String name, String key, TaskStatus status, Instant created, Instant completed,
            boolean cancellable, Duration lastLag, Duration worstLag) {
        return TaskSnapshot.builder(TaskId.random(), "pnTest", TaskExecution.Kind.GLOBAL, status, created)
                .name(name)
                .key(key)
                .tags(Collections.<String>emptySet())
                .runCount(0)
                .skippedCount(0)
                .lastCompletedAt(completed)
                .cancellable(cancellable)
                .lastLag(lastLag)
                .worstLag(worstLag)
                .build();
    }
}
