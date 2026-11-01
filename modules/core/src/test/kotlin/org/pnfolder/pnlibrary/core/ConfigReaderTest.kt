package org.pnfolder.pnlibrary.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration
import org.pnfolder.pnlibrary.core.diagnostics.ConfigReader
import java.nio.file.Files
import java.nio.file.Path

class ConfigReaderTest {

    @TempDir
    lateinit var dataFolder: Path

    @Test
    fun `reads yaml file and applies exclusions and secret redaction`() {
        val configFile = dataFolder.resolve("config.yml").toFile()
        configFile.writeText(
            """
            storage:
              host: "localhost"
              password: "my_db_password"
              internalPool:
                maxSize: 10
            featureFlags:
              beta: true
            licenseKey: "license-ABC123XYZ"
            """.trimIndent()
        )

        val spec = DiagnosticConfiguration.builder("config.yml")
            .exclude("storage.internalPool")
            .secretKeyRegex("(?i).*password.*")
            .redactValueRegex("license-[A-Za-z0-9]+")
            .build()

        val reader = ConfigReader(dataFolder)
        val result = reader.readAndRedact(spec)

        assertNotNull(result["data"])
        @Suppress("UNCHECKED_CAST")
        val data = result["data"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val storage = data["storage"] as Map<String, Any?>

        assertEquals("localhost", storage["host"])
        assertEquals("[REDACTED SECRET KEY]", storage["password"])
        assertEquals("[EXCLUDED BY RULE]", storage["internalPool"])
        assertEquals("[REDACTED VALUE]", data["licenseKey"])
    }

    @Test
    fun `administrator path with a file prefix hides the value only in that file`() {
        val plugin = Files.createDirectory(dataFolder.resolve("pnExample"))
        plugin.resolve("config.yml").toFile().writeText("note: a\nother: b")
        plugin.resolve("messages.yml").toFile().writeText("note: a\nother: b")
        val reader = ConfigReader(
            dataFolder,
            org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig(
                excludedPaths = listOf("pnExample/config.yml:note", "other"),
            ),
        )

        fun values(file: String): Map<String, Any?> {
            @Suppress("UNCHECKED_CAST")
            val data = reader.readAndRedact(DiagnosticConfiguration.builder(file).build(), plugin)["data"] as Map<String, Any?>
            return data
        }

        assertEquals(mapOf("note" to "[EXCLUDED BY RULE]", "other" to "[EXCLUDED BY RULE]"), values("config.yml"))
        assertEquals(mapOf("note" to "a", "other" to "[EXCLUDED BY RULE]"), values("messages.yml"))
    }

    @Test
    fun `blocks path traversal`() {
        val spec = DiagnosticConfiguration.builder("config.yml").build()
        val reader = ConfigReader(dataFolder)

        // Try reading outside directory
        val result = reader.readAndRedact(spec)
        assertNotNull(result["error"])
    }

    @Test
    fun `blocks escape through a symlinked parent directory`() {
        val outside = Files.createTempDirectory("pnlibrary-outside")
        try {
            Files.writeString(outside.resolve("config.yml"), "safe: false\n")
            val link = dataFolder.resolve("linked")
            val linked = runCatching { Files.createSymbolicLink(link, outside); true }.getOrDefault(false)
            assumeTrue(linked, "symbolic links are unavailable on this test host")

            val spec = DiagnosticConfiguration.builder("linked/config.yml").build()
            val result = ConfigReader(dataFolder).readAndRedact(spec)

            assertEquals("[SECURITY: symlink escape blocked]", result["error"])
        } finally {
            runCatching { Files.deleteIfExists(outside.resolve("config.yml")) }
            runCatching { Files.deleteIfExists(outside) }
        }
    }
}
