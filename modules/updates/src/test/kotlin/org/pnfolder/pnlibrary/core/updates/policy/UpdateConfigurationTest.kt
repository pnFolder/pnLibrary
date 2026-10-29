package org.pnfolder.pnlibrary.core.updates.policy

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import java.nio.file.Files
import java.nio.file.Path

class UpdateConfigurationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `lists plugins that are never updated and normalizes their identifiers`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, """
            plugins:
              ignored: [PnCases, acceptance]
        """.trimIndent())

        val configuration = UpdateConfiguration.load(file)

        assertEquals(setOf("pncases", "acceptance"), configuration.ignoredPlugins)
    }

    @Test
    fun `reads library and plugin download policy`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, """
            library:
              channel: beta
              automatic-download: true
            plugins:
              automatic-download: false
        """.trimIndent())

        val configuration = UpdateConfiguration.load(file)

        // the library channel is fixed to dev in code; the file value is not applied
        assertEquals(UpdateChannel.DEV, configuration.library.channel)
        assertTrue(configuration.library.automaticDownload)
        assertFalse(configuration.pluginAutomaticDownload)
        assertTrue(configuration.ignoredPlugins.isEmpty())
    }

    @Test
    fun `per-plugin policies and pause are not file options and are removed with a warning`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, """
            plugins:
              policies:
                acceptance: { update: paused }
        """.trimIndent())
        val warnings = mutableListOf<String>()

        val configuration = UpdateConfiguration.load(file, warnings::add)

        assertTrue(configuration.ignoredPlugins.isEmpty())
        assertTrue(warnings.any { it.contains("policies") })
        assertFalse(Files.readString(file).contains("policies"))
    }

    @Test
    fun `an invalid plugin identifier makes the file fall back to safe defaults`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, "plugins:\n  ignored: [\"bad id!\"]\n")
        val warnings = mutableListOf<String>()

        val configuration = UpdateConfiguration.load(file, warnings::add)

        assertTrue(configuration.ignoredPlugins.isEmpty())
        assertTrue(warnings.isNotEmpty())
    }

    @Test
    fun `switches, intervals and limits of the old file are ignored and cannot weaken the fixed rules`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, """
            enabled: false
            checks: { enabled: false, interval: 30m }
            notifications: { console: false, repeat-interval: 1d }
            downloads: { connect-timeout: 1s, max-artifact-megabytes: 1 }
            installation: { require-sha256: false }
            plugins: { enabled: false }
        """.trimIndent())
        val warnings = mutableListOf<String>()

        val configuration = UpdateConfiguration.load(file, warnings::add)

        assertEquals(UpdateDefaults.CHECK_INTERVAL, configuration.checks.interval)
        assertEquals(UpdateDefaults.NOTIFICATION_REPEAT, configuration.notifications.repeatInterval)
        assertEquals(UpdateDefaults.CONNECT_TIMEOUT, configuration.downloads.connectTimeout)
        assertEquals(UpdateDefaults.MAX_ARTIFACT_BYTES, configuration.downloads.maxArtifactBytes)
        assertTrue(configuration.installation.requireSha256)
        val text = Files.readString(file)
        listOf("checks", "notifications", "downloads", "require-sha256", "interval").forEach { assertFalse(text.contains(it), it) }
    }

    @Test
    fun `creates a short commented file with only the choices of an administrator`() {
        val warnings = mutableListOf<String>()
        val file = directory.resolve("updates.yml")
        val configuration = UpdateConfiguration.load(file, warnings::add)

        assertFalse(configuration.effectiveAutomaticDownloads)
        assertFalse(configuration.installation.allowNewPlugins)
        assertTrue(configuration.installation.requireSha256)
        assertTrue(warnings.isEmpty())
        val text = Files.readString(file)
        assertTrue(text.contains("allow-new-plugins: false"))
        assertTrue(text.contains("ignored: []"))
        assertFalse(text.contains("interval"))
    }

    @Test
    fun `an unreadable file falls back to safe defaults with a warning`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, "plugins: [unclosed\n")
        val warnings = mutableListOf<String>()

        val configuration = UpdateConfiguration.load(file, warnings::add)

        assertFalse(configuration.effectiveAutomaticDownloads)
        assertTrue(warnings.isNotEmpty())
    }

    @Test
    fun `an invalid flag falls back to its safe default without granting rights`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, """
            library: { automatic-download: perhaps }
            installation: { allow-new-plugins: perhaps }
        """.trimIndent())
        val warnings = mutableListOf<String>()

        val configuration = UpdateConfiguration.load(file, warnings::add)

        assertFalse(configuration.library.automaticDownload)
        assertFalse(configuration.installation.allowNewPlugins)
        assertTrue(warnings.isNotEmpty())
    }
}
