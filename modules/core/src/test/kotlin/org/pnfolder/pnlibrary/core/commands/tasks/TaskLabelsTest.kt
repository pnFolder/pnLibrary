package org.pnfolder.pnlibrary.core.commands.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.tasks.info.TaskId
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution
import java.time.Duration
import java.time.Instant

class TaskLabelsTest {
    @Test
    fun `a span is written in the largest fitting unit and drops empty remainders`() {
        assertEquals("250 мс", TaskLabels.span(Duration.ofMillis(250)))
        assertEquals("5 с", TaskLabels.span(Duration.ofSeconds(5)))
        assertEquals("3 мин", TaskLabels.span(Duration.ofMinutes(3)))
        assertEquals("2 ч", TaskLabels.span(Duration.ofHours(2)))
        assertEquals("2 ч 30 мин", TaskLabels.span(Duration.ofMinutes(150)))
        assertEquals("1 ч 10 мин", TaskLabels.span(Duration.ofMinutes(70)))
        assertEquals("3 д", TaskLabels.span(Duration.ofDays(3)))
        assertEquals("1 д 10 ч", TaskLabels.span(Duration.ofHours(34)))
    }

    @Test
    fun `ago says just now, how long ago, or how long until`() {
        assertEquals("только что", TaskLabels.ago(Instant.now().minusSeconds(10)))
        assertEquals("только что", TaskLabels.ago(Instant.now().plusSeconds(10)))
        assertTrue(TaskLabels.ago(Instant.now().minus(Duration.ofMinutes(5))).endsWith("назад"))
        assertTrue(TaskLabels.ago(Instant.now().plus(Duration.ofHours(2).plusSeconds(30))).startsWith("через"))
    }

    @Test
    fun `every status has its own words`() {
        assertEquals(TaskStatus.values().size, TaskStatus.values().map(TaskLabels::label).toSet().size)
    }

    @Test
    fun `a lag is mentioned only when it is noticeable`() {
        assertNull(TaskLabels.lag(task(worstLag = Duration.ofMillis(400))))
        assertTrue(TaskLabels.lag(task(worstLag = Duration.ofSeconds(3), lastLag = Duration.ofSeconds(1)))!!.contains("наибольшее 3 с"))
    }

    @Test
    fun `stop and run answers say why not`() {
        assertEquals("да", TaskLabels.stopAnswer(task()))
        assertTrue(TaskLabels.stopAnswer(task(cancellable = false)).contains("плагин запретил"))
        assertTrue(TaskLabels.runAnswer(task(status = TaskStatus.RUNNING)).contains("выполняется"))
        assertFalse(TaskLabels.runAnswer(task(status = TaskStatus.COMPLETED)).startsWith("да"))
    }

    @Test
    fun `the title falls back from name to key to a short id`() {
        assertEquals("cleanup", TaskLabels.title(task(name = "cleanup")))
        assertEquals("k1", TaskLabels.title(task(name = null, key = "k1")))
        assertEquals(TaskLabels.SHORT_ID, TaskLabels.title(task(name = null, key = null)).length)
    }

    @Test
    fun `an older finished task falls out of today and the week but active ones always stay`() {
        val old = task(status = TaskStatus.COMPLETED, created = Instant.now().minus(Duration.ofDays(30)), completed = Instant.now().minus(Duration.ofDays(30)))
        val running = task(status = TaskStatus.RUNNING, created = Instant.now().minus(Duration.ofDays(30)))

        assertFalse(TaskPeriod.TODAY.keep(old))
        assertFalse(TaskPeriod.WEEK.keep(old))
        assertTrue(TaskPeriod.OLDER.keep(old))
        assertTrue(TaskPeriod.ANY.keep(old))
        assertTrue(TaskPeriod.TODAY.keep(running))
        assertFalse(TaskPeriod.OLDER.keep(running))
    }

    private fun task(
        name: String? = "task",
        key: String? = null,
        status: TaskStatus = TaskStatus.SCHEDULED,
        created: Instant = Instant.now(),
        completed: Instant? = null,
        cancellable: Boolean = true,
        lastLag: Duration? = null,
        worstLag: Duration? = null,
    ) = TaskSnapshot(
        id = TaskId.random(), name = name, key = key, ownerName = "pnTest", executionKind = TaskExecution.Kind.GLOBAL,
        status = status, tags = emptySet(), createdAt = created, nextRunAt = null, runCount = 0, skippedCount = 0,
        lastStartedAt = null, lastCompletedAt = completed, lastFailure = null, cancellable = cancellable,
        lastLag = lastLag, worstLag = worstLag,
    )
}
