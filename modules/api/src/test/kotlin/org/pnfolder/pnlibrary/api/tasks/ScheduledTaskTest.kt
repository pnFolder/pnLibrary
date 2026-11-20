package org.pnfolder.pnlibrary.api.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.tasks.run.TaskContext
import org.pnfolder.pnlibrary.api.tasks.spec.ScheduledTask
import org.pnfolder.pnlibrary.api.tasks.spec.TaskConflictPolicy
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution
import java.time.Duration

class ScheduledTaskTest {
    private class Autosave(private val world: String) : ScheduledTask() {
        var active = true
        override val name get() = "Autosave $world"
        override val key get() = "autosave-$world"
        override val interval: Duration get() = Duration.ofMinutes(5)
        override val execution get() = TaskExecution.async()
        override val tags get() = setOf("save")

        override fun shouldRun() = active

        override fun run(context: TaskContext) = Unit
    }

    @Test
    fun `a task class becomes a spec with its own arguments`() {
        val spec = Autosave("nether").toSpec()

        assertEquals("Autosave nether", spec.name)
        assertEquals("autosave-nether", spec.key)
        assertEquals(Duration.ofMinutes(5), spec.interval)
        assertEquals(TaskExecution.Kind.ASYNC, spec.execution.kind)
        assertEquals(setOf("save"), spec.tags)
        assertEquals(TaskConflictPolicy.REJECT, spec.conflictPolicy)
    }

    @Test
    fun `the run conditions follow the task`() {
        val task = Autosave("world")
        val spec = task.toSpec()

        assertTrue(spec.conditions.all { it.asBoolean })
        task.active = false
        assertFalse(spec.conditions.all { it.asBoolean })
        assertFalse(spec.cancellationConditions.any { it.asBoolean })
    }
}
