package ru.privatenull.pnlibrary.api.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig

class TaskSpecTest {
    @Test
    fun `duplicate display names are valid while ids remain distinct`() {
        val first = TaskSpec.builder().name("player-hud").action { }.build()
        val second = TaskSpec.builder().name("player-hud").action { }.build()

        assertEquals(first.name, second.name)
        assertNotEquals(TaskId.random(), TaskId.random())
    }

    @Test
    fun `builder rejects invalid scheduling values before registration`() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskSpec.builder().key(" ").action { }.build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskSpec.builder().tag(" ").action { }.build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskSpec.builder().delay(Duration.ofSeconds(-1)).action { }.build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskSpec.builder().interval(Duration.ZERO).action { }.build()
        }
        assertThrows(IllegalStateException::class.java) { TaskSpec.builder().build() }
        assertThrows(IllegalArgumentException::class.java) { TaskServiceSettings(-1) }
        assertThrows(IllegalArgumentException::class.java) { PnLibraryConfig(taskHistoryCapacity = -1) }
    }

    @Test
    fun `execution factories and builder are directly visible to Java`() {
        assertEquals(TaskExecution.Kind.GLOBAL, TaskExecution.global().kind)
        assertEquals(TaskExecution.Kind.ASYNC, TaskExecution.async().kind)
        assertEquals("player", TaskExecution.entity("player").target)
        assertNotNull(TaskSpec::class.java.getMethod("builder"))
    }
}
