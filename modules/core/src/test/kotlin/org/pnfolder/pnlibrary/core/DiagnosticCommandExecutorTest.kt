package org.pnfolder.pnlibrary.core

import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticCommandEvent
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticCommandExecutor
import org.pnfolder.pnlibrary.core.runtime.PnLibraryLauncher
import org.pnfolder.pnlibrary.core.runtime.platform.toRuntimeContext
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DiagnosticCommandExecutorTest {
    @TempDir lateinit var temporary: Path

    @Test
    fun `command flow creates report and publishes completion`() {
        val platform = TestPlatform(temporary)
        val library = PnLibraryLauncher.bootstrap(
            Any(),
            platform.toRuntimeContext(),
            PnLibraryConfig(upload = false, uploadMode = "disabled", cooldown = Duration.ZERO),
        )
        val events = CopyOnWriteArrayList<DiagnosticCommandEvent>()
        val completed = CountDownLatch(1)

        try {
            DiagnosticCommandExecutor(library).execute(
                arguments = arrayOf("all", "--local"),
                prefixed = false,
                requesterId = "console",
                recipient = Any(),
            ) { event ->
                events += event
                if (event is DiagnosticCommandEvent.Completed) completed.countDown()
            }

            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertInstanceOf(DiagnosticCommandEvent.Started::class.java, events.first())
            val result = events.filterIsInstance<DiagnosticCommandEvent.Completed>().single().report
            assertTrue(Files.isRegularFile(result.localFile))
        } finally {
            library.close()
        }
    }

    private class TestPlatform(override val dataFolder: Path) : PlatformAdapter {
        override val type = PlatformType.BUKKIT
        override val id = "test"
        override val taskAdapter = object : PlatformTaskAdapter {
            override fun schedule(request: PlatformTaskRequest): PlatformTaskHandle {
                request.callback.run()
                return PlatformTaskHandle { false }
            }
        }
        override fun ownerDetails(owner: Any) = mapOf("name" to "pnLibrary", "version" to "2.0.0")
        override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) = task.run()
        override fun executeReply(recipient: Any, task: Runnable) = task.run()
        override fun close() = Unit
    }
}
