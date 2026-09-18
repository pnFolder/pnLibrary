package ru.privatenull.pnlibrary.core

import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticCommandEvent
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticCommandExecutor
import ru.privatenull.pnlibrary.core.runtime.PnLibraryBootstrap
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DiagnosticCommandExecutorTest {
    @TempDir lateinit var temporary: Path

    @Test
    fun `command flow creates report and publishes completion`() {
        val platform = TestPlatform(temporary)
        val library = PnLibraryBootstrap.bootstrap(
            Any(),
            platform,
            PnLibraryConfig(upload = false, uploadMode = "disabled", cooldownSeconds = 0),
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
            assertTrue(java.nio.file.Files.isRegularFile(result.localFile))
        } finally {
            library.close()
        }
    }

    private class TestPlatform(override val dataFolder: Path) : PlatformAdapter {
        override val type = PlatformType.BUKKIT
        override val id = "test"
        override fun ownerDetails(owner: Any) = mapOf("name" to "pnLibrary", "version" to "2.0.0")
        override fun details(): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) = task.run()
        override fun executeReply(recipient: Any, task: Runnable) = task.run()
        override fun close() = Unit
    }
}
