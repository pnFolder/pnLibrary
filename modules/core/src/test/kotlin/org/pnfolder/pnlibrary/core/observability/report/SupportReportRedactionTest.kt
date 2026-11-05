package org.pnfolder.pnlibrary.core.observability.report

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticContainer
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

class SupportReportRedactionTest {
    @TempDir lateinit var temporary: Path

    private fun report(yaml: String, build: DiagnosticConfiguration.Builder.() -> Unit): String {
        val folder = temporary.resolve("pnTest")
        Files.createDirectories(folder)
        Files.writeString(folder.resolve("config.yml"), yaml)
        val registry = DiagnosticsRegistry()
        val configuration = DiagnosticConfiguration.builder("config.yml").apply(build).build()
        registry.register("pnTest", folder, DiagnosticContainer.builder("pnTest").configuration(configuration).build())
        val platform = Platform()
        val bytes = SupportReportAssembler(
            dataFolder = temporary,
            config = PnLibraryConfig(upload = false, uploadMode = "disabled"),
            diagnostics = registry,
            identity = platform as PlatformIdentity,
            diagnosticsPlatform = platform,
            diagnosticLogs = { emptyList() },
            diagnosticHistory = { emptyList() },
            observabilitySnapshot = { org.pnfolder.pnlibrary.core.observability.ObservabilityReportSnapshot(ByteArray(0), ByteArray(0), emptyMap()) },
        ).assemble(DebugRequest("all", configs = true, logs = false, local = true), encrypted = false)
        val file = temporary.resolve("report.zip")
        Files.write(file, bytes)
        ZipFile(file.toFile()).use { zip ->
            return zip.entries().asSequence().filter { it.name.endsWith("config.yml") }
                .joinToString("\n") { zip.getInputStream(it).readBytes().decodeToString() }
        }
    }

    @Test
    fun `excluded paths and secret key patterns never reach the report`() {
        val text = report("db:\n  host: localhost\n  pass: hunter2xyz\napi: tokenvalue123\nname: visible-name\n") {
            exclude("db.pass"); secretKeyRegex("^api$")
        }
        assertFalse(text.contains("hunter2xyz"), text)
        assertFalse(text.contains("tokenvalue123"), text)
        assertTrue(text.contains("visible-name"), text)
    }

    @Test
    fun `a value pattern hides the matching part`() {
        val text = report("note: leak-me-please\n") {
            redactValueRegex("leak")
        }
        assertFalse(text.contains("leak"), text)
        assertTrue(text.contains("REDACTED"), text)
    }

    private class Platform : PlatformAdapter {
        override val type = PlatformType.BUKKIT
        override val id = "test"
        override val dataFolder: Path = Files.createTempDirectory("pnlibrary-redaction-test")
        override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) = task.run()
        override fun executeReply(recipient: Any, task: Runnable) = task.run()
        override fun close() = Unit
    }
}
