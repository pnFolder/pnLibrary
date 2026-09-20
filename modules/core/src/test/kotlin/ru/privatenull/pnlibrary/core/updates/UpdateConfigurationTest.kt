package ru.privatenull.pnlibrary.core.updates

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import ru.privatenull.pnlibrary.api.updates.UpdateChannel

class UpdateConfigurationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `reads per-component channel automatic policy and relative pause`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, """
            updates:
              components:
                pncases:
                  channel: beta
                  automatic: false
                  pause: 7d
        """.trimIndent())

        val configuration = UpdateConfiguration.load(file)
        val policy = configuration.components.getValue("pncases")

        assertEquals(UpdateChannel.BETA, policy.channel)
        assertEquals(false, policy.automatic)
        assertEquals(Duration.ofDays(7), policy.pause)
    }

    @Test
    fun `creates conservative complete defaults`() {
        val warnings = mutableListOf<String>()
        val configuration = UpdateConfiguration.load(directory.resolve("updates.yml"), warnings::add)

        assertTrue(configuration.enabled)
        assertTrue(configuration.checks.enabled)
        assertEquals(Duration.ofMinutes(30), configuration.checks.interval)
        assertEquals(Duration.ofHours(6), configuration.notifications.repeatInterval)
        assertFalse(configuration.downloads.automatic)
        assertFalse(configuration.downloads.allowExternalUrls)
        assertFalse(configuration.installation.allowNewPlugins)
        assertFalse(configuration.installation.restartAfterConfirmation)
        assertTrue(warnings.isEmpty())
        assertTrue(Files.readString(directory.resolve("updates.yml")).contains("maximum-online-for-one-click: 10"))
    }

    @Test
    fun `migrates legacy policy once and preserves a backup`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, "channel: beta\nauto-download: true\n")

        val configuration = UpdateConfiguration.load(file) { }

        assertTrue(configuration.downloads.automatic)
        assertEquals("beta", configuration.legacyChannel)
        assertTrue(Files.isRegularFile(directory.resolve("updates.yml.pre-orchestrator.bak")))
        assertTrue(Files.readString(file).startsWith("updates:"))
    }

    @Test
    fun `malformed values fall back without granting dangerous capabilities`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, """
            updates:
              enabled: perhaps
              checks: { enabled: true, interval: never }
              downloads: { automatic: yes, allow-external-urls: yes, allowed-hosts: ["bad host"] }
              installation: { allow-new-plugins: yes, restart-after-confirmation: yes }
        """.trimIndent())
        val warnings = mutableListOf<String>()

        val configuration = UpdateConfiguration.load(file, warnings::add)

        assertTrue(configuration.enabled)
        assertEquals(Duration.ofMinutes(30), configuration.checks.interval)
        assertFalse(configuration.downloads.automatic)
        assertFalse(configuration.downloads.allowExternalUrls)
        assertFalse(configuration.installation.allowNewPlugins)
        assertFalse(configuration.installation.restartAfterConfirmation)
        assertEquals(1, warnings.size)
    }

    @Test
    fun `legacy master disable keeps checks and warnings but blocks mutations`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, """
            updates:
              enabled: false
              checks: { enabled: true, interval: 1m }
              notifications: { console: true, administrators: true }
              downloads: { automatic: true, allow-managed-plugins: true }
              installation: { allow-new-plugins: true, restart-after-confirmation: true }
        """.trimIndent())

        val configuration = UpdateConfiguration.load(file) { }

        assertTrue(configuration.effectiveChecksEnabled)
        assertTrue(configuration.effectiveConsoleNotifications)
        assertTrue(configuration.effectiveAdministratorNotifications)
        assertFalse(configuration.effectiveAutomaticDownloads)
        assertFalse(configuration.effectiveRestart)
    }
}
