package ru.privatenull.pnlibrary.spi.tasks

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import java.time.Duration

class PlatformTaskAdapterTest {
    @Test
    fun `entity request requires a target`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlatformTaskRequest(TaskExecution.Kind.ENTITY, null, Duration.ZERO, null, Runnable {})
        }
    }

    @Test
    fun `unsupported adapter rejects registration`() {
        assertThrows(UnsupportedOperationException::class.java) {
            UnsupportedPlatformTaskAdapter.schedule(
                PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, Runnable {}),
            )
        }
    }
}
