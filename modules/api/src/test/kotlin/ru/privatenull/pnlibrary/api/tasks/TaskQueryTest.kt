package ru.privatenull.pnlibrary.api.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TaskQueryTest {
    @Test
    fun `builder creates normalized immutable collection snapshots`() {
        val query = TaskQuery.builder()
            .key("  cleanup  ")
            .nameContains("  nightly  ")
            .status(TaskStatus.SCHEDULED)
            .execution(TaskExecution.Kind.ASYNC)
            .tag("maintenance")
            .build()

        assertEquals("cleanup", query.key)
        assertEquals("nightly", query.nameContains)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (query.tags as MutableSet<String>).add("changed")
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (query.statuses as MutableSet<TaskStatus>).clear()
        }
    }

    @Test
    fun `task declaration collections are immutable`() {
        val spec = TaskSpec.builder()
            .condition { true }
            .cancelWhen { false }
            .tag("maintenance")
            .action { }
            .build()

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (spec.tags as MutableSet<String>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (spec.conditions as MutableList<java.util.function.BooleanSupplier>).clear()
        }
    }

    @Test
    fun `builder rejects meaningless blank filters`() {
        assertThrows(IllegalArgumentException::class.java) { TaskQuery.builder().key(" ").build() }
        assertThrows(IllegalArgumentException::class.java) { TaskQuery.builder().nameContains(" ").build() }
        assertThrows(IllegalArgumentException::class.java) { TaskQuery.builder().tag(" ").build() }
    }
}
