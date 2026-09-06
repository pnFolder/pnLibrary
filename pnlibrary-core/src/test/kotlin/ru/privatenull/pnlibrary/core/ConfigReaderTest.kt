package ru.privatenull.pnlibrary.core

import ru.privatenull.pnlibrary.core.diagnostics.ConfigReader


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticConfiguration
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

        val spec = DiagnosticConfiguration.file("config.yml")
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
    fun `blocks path traversal`() {
        val spec = DiagnosticConfiguration.file("config.yml").build()
        val reader = ConfigReader(dataFolder)

        // Try reading outside directory
        val result = reader.readAndRedact(spec)
        assertNotNull(result["error"])
    }
}
