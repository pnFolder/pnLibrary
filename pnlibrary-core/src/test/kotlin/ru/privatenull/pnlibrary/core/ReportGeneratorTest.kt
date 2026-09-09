package ru.privatenull.pnlibrary.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.diagnostics.DebugRequest
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticsRegistry
import ru.privatenull.pnlibrary.core.diagnostics.ReportGenerator
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

class ReportGeneratorTest {
    @TempDir lateinit var temporary: Path

    @Test
    fun `full all report includes every plugin config and buffered logs`() {
        val registry = DiagnosticsRegistry()
        listOf("pnMarket", "pnClans").forEach { plugin ->
            val folder = temporary.resolve(plugin)
            Files.createDirectories(folder)
            Files.writeString(folder.resolve("config.yml"), "enabled: true\n")
            registry.register(plugin, folder, DiagnosticContainer.builder(plugin).configuration("config.yml").build())
        }
        val generator = ReportGenerator(
            dataFolder = temporary,
            config = PnLibraryConfig(upload = false, uploadMode = "disabled"),
            diagnosticsRegistry = registry,
            platformAdapter = TestPlatform(),
            encryptionCodec = null,
            uploader = null,
            uploadLedger = null,
            diagnosticLogs = { listOf(mapOf("level" to "WARNING", "message" to "test")) },
        )

        val result = generator.generateAndSave(DebugRequest("all", configs = true, logs = true, local = true))
        ZipFile(result.localFile.toFile()).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue("pn-diagnostic/plugins/pnmarket/configuration/config.yml.json" in names)
            assertTrue("pn-diagnostic/plugins/pnclans/configuration/config.yml.json" in names)
            assertTrue("pn-diagnostic/plugins/runtime/logs/incidents.json" in names)
            assertTrue("pn-diagnostic/checksums.json" in names)
        }
    }

    private class TestPlatform : PlatformAdapter {
        override val type = PlatformType.BUKKIT
        override val id = "test"
        override fun details(): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) = task.run()
        override fun executeReply(recipient: Any, task: Runnable) = task.run()
        override fun close() = Unit
    }
}
