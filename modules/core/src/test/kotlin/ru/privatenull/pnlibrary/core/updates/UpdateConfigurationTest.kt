package ru.privatenull.pnlibrary.core.updates

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import ru.privatenull.pnlibrary.api.updates.UpdateChannel

class UpdateConfigurationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `reads library download policy and per-plugin update policy`() {
        val file = directory.resolve("updates.yml")
        val pauseUntil = Instant.now().plus(Duration.ofDays(3)).toString()
        Files.writeString(file, """
            updates:
              library:
                automatic-download: false
              plugins:
                enabled: true
                automatic-download: false
                plugins:
                  pncases:
                    channel: beta
                    mode: disabled
                    automatic-download: true
                    pause-until: "$pauseUntil"
        """.trimIndent())

        val configuration = UpdateConfiguration.load(file)
        val policy = configuration.plugins.getValue("pncases")

        assertFalse(configuration.library.automaticDownload)
        assertTrue(configuration.pluginUpdatesEnabled)
        assertFalse(configuration.pluginAutomaticDownload)
        assertEquals(UpdateChannel.BETA, policy.channel)
        assertFalse(policy.enabled)
        assertTrue(policy.automaticDownload)
        assertEquals(Instant.parse(pauseUntil), policy.pauseUntil)
    }

    @Test
    fun `user check interval is ignored because developer owns scheduling`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, """
            updates:
              checks: { enabled: false, interval: 30d }
              library: { automatic-download: false }
        """.trimIndent())

        val configuration = UpdateConfiguration.load(file)

        assertTrue(configuration.effectiveChecksEnabled)
        assertEquals(Duration.ofHours(6), configuration.checks.interval)
    }

    @Test
    fun `plugin pause longer than seven days is rejected conservatively`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, """
            updates:
              plugins:
                plugins:
                  pncases: { pause-until: "${Instant.now().plus(Duration.ofDays(8))}" }
        """.trimIndent())
        val warnings = mutableListOf<String>()

        val configuration = UpdateConfiguration.load(file, warnings::add)

        assertNull(configuration.plugins.getValue("pncases").pauseUntil)
        assertEquals(1, warnings.size)
    }

    @Test
    fun `creates conservative complete defaults`() {
        val warnings = mutableListOf<String>()
        val configuration = UpdateConfiguration.load(directory.resolve("updates.yml"), warnings::add)

        assertTrue(configuration.enabled)
        assertTrue(configuration.checks.enabled)
        assertEquals(Duration.ofHours(6), configuration.checks.interval)
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
        assertTrue(Files.readString(file).contains("updates:"))
    }

    @Test
    fun `malformed values fall back without granting dangerous capabilities`() {
        val file = directory.resolve("updates.yml")
        Files.writeString(file, """
            updates:
              enabled: perhaps
              checks: { enabled: true, interval: never }
              downloads: { automatic: yes, allow-external-urls: yes }
              installation: { allow-new-plugins: yes, restart-after-confirmation: yes }
        """.trimIndent())
        val warnings = mutableListOf<String>()

        val configuration = UpdateConfiguration.load(file, warnings::add)

        assertTrue(configuration.enabled)
        assertEquals(Duration.ofHours(6), configuration.checks.interval)
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
