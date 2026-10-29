package org.pnfolder.pnlibrary.core.updates.policy

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

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
              enabled: true
              automatic-download: false
        """.trimIndent())

        val configuration = UpdateConfiguration.load(file)

        assertEquals(UpdateChannel.BETA, configuration.library.channel)
        assertTrue(configuration.library.automaticDownload)
        assertTrue(configuration.pluginUpdatesEnabled)
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
    fun `check interval is configurable but checks cannot be switched off`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, "checks: { enabled: false, interval: 30m }\n")
        val warnings = mutableListOf<String>()

        val configuration = UpdateConfiguration.load(file, warnings::add)

        assertTrue(configuration.effectiveChecksEnabled)
        assertEquals(Duration.ofMinutes(30), configuration.checks.interval)
    }

    @Test
    fun `check interval below the minimum is raised to it`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, "checks: { interval: 1m }\n")
        val warnings = mutableListOf<String>()

        val configuration = UpdateConfiguration.load(file, warnings::add)

        assertEquals(Duration.ofMinutes(5), configuration.checks.interval)
    }

    @Test
    fun `creates a conservative commented file`() {
        val warnings = mutableListOf<String>()
        val file = directory.resolve("updates.yml")
        val configuration = UpdateConfiguration.load(file, warnings::add)

        assertTrue(configuration.enabled)
        assertTrue(configuration.checks.enabled)
        assertEquals(Duration.ofHours(6), configuration.notifications.repeatInterval)
        assertFalse(configuration.downloads.automatic)
        assertFalse(configuration.installation.allowNewPlugins)
        assertTrue(configuration.installation.requireSha256)
        assertTrue(warnings.isEmpty())
        val text = Files.readString(file)
        assertTrue(text.contains("require-sha256: true"))
        assertTrue(text.contains("repeat-interval: 6h"))
        assertTrue(text.contains("# Главный выключатель автоматических действий"))
    }

    @Test
    fun `an unreadable file falls back to safe defaults with a warning`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, "enabled: [unclosed\n")
        val warnings = mutableListOf<String>()

        val configuration = UpdateConfiguration.load(file, warnings::add)

        assertFalse(configuration.downloads.automatic)
        assertTrue(warnings.isNotEmpty())
    }

    @Test
    fun `an invalid flag falls back to its safe default without granting rights`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, """
            downloads: { automatic: perhaps }
            installation: { allow-new-plugins: perhaps }
        """.trimIndent())
        val warnings = mutableListOf<String>()

        val configuration = UpdateConfiguration.load(file, warnings::add)

        assertFalse(configuration.downloads.automatic)
        assertFalse(configuration.installation.allowNewPlugins)
        assertTrue(warnings.isNotEmpty())
    }

    @Test
    fun `master disable keeps checks and warnings but blocks automatic downloads`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, """
            enabled: false
            notifications: { console: true }
            downloads: { automatic: true, allow-managed-plugins: true }
            installation: { allow-new-plugins: true }
        """.trimIndent())

        val configuration = UpdateConfiguration.load(file) { }

        assertTrue(configuration.effectiveChecksEnabled)
        assertTrue(configuration.effectiveConsoleNotifications)
        assertFalse(configuration.effectiveAutomaticDownloads)
    }
}
