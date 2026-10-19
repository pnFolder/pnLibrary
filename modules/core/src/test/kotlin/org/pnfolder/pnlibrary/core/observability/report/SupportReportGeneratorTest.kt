package org.pnfolder.pnlibrary.core.observability.report

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticContainer
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

class SupportReportGeneratorTest {
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
        val generator = SupportReportGenerator(
            dataFolder = temporary,
            config = PnLibraryConfig(upload = false, uploadMode = "disabled"),
            diagnosticsRegistry = registry,
            platformIdentity = TestPlatform(),
            platformDiagnostics = TestPlatform(),
            encryptionCodec = null,
            uploader = null,
            uploadLedger = null,
            diagnosticLogs = { listOf(mapOf("level" to "WARNING", "message" to "test")) },
        )

        val result = generator.generateAndSave(DebugRequest("all", configs = true, logs = true, local = true))
        ZipFile(result.localFile.toFile()).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue("pn-diagnostic/plugins/pnmarket/configuration/config.yml" in names)
            assertTrue("pn-diagnostic/plugins/pnclans/configuration/config.yml" in names)
            assertTrue("pn-diagnostic/plugins/runtime/logs/incidents.json" in names)
            assertTrue("pn-diagnostic/checksums.json" in names)
        }
    }

    private class TestPlatform : PlatformAdapter {
        override val type = PlatformType.BUKKIT
        override val id = "test"
        override val dataFolder = Files.createTempDirectory("pnlibrary-report-test")
        override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) = task.run()
        override fun executeReply(recipient: Any, task: Runnable) = task.run()
        override fun close() = Unit
    }
}
