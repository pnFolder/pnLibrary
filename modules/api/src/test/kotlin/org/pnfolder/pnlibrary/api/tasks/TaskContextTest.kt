package org.pnfolder.pnlibrary.api.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.tasks.info.TaskId
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.run.TaskContext
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec
import java.time.Duration
import java.time.Instant

class TaskContextTest {
    private class FakeContext(
        override val runNumber: Long,
        override val scheduledAt: Instant = Instant.EPOCH,
        override val startedAt: Instant = Instant.EPOCH,
        override val interval: Duration? = null,
    ) : TaskContext {
        var cancelled = false
        override val id = TaskId("t")
        override val name: String? = null
        override val key: String? = null
        override val parentId: TaskId? = null
        override val tags: Set<String> = emptySet()
        override val executionKind = TaskExecution.Kind.GLOBAL
        override val execution = TaskExecution.global()
        override fun spawn(spec: TaskSpec): TaskHandle = error("not needed")
        override val skippedCount = 0L
        override val previousStartedAt: Instant? = null
        override val isCancelled get() = cancelled
        override fun cancel() { cancelled = true }
        override fun snapshot(): TaskSnapshot = error("not needed")
    }

    @Test
    fun `run counters answer first, every and cancel after`() {
        assertTrue(FakeContext(1).isFirstRun)
        assertFalse(FakeContext(2).isFirstRun)
        assertTrue(FakeContext(10).every(5))
        assertFalse(FakeContext(11).every(5))
        assertThrows(IllegalArgumentException::class.java) { FakeContext(1).every(0) }

        val early = FakeContext(2)
        assertFalse(early.cancelAfter(3))
        assertFalse(early.cancelled)
        val last = FakeContext(3)
        assertTrue(last.cancelAfter(3))
        assertTrue(last.cancelled)
    }

    @Test
    fun `lag is never negative and repeating follows the interval`() {
        assertEquals(Duration.ofMillis(300), FakeContext(1, Instant.EPOCH, Instant.ofEpochMilli(300)).lag)
        assertEquals(Duration.ZERO, FakeContext(1, Instant.ofEpochMilli(500), Instant.EPOCH).lag)
        assertTrue(FakeContext(1, interval = Duration.ofSeconds(1)).isRepeating)
        assertFalse(FakeContext(1).isRepeating)
    }
}
