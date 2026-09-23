package ru.privatenull.pnlibrary.core.downloads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.plugin.Dependencies
import ru.privatenull.pnlibrary.api.plugin.DownloadPolicy
import ru.privatenull.pnlibrary.api.updates.ExternalPluginDependency
import ru.privatenull.pnlibrary.core.updates.UpdateConfiguration

class DependencyDownloadPolicyTest {
    private val policy = DependencyDownloadPolicy()

    @Test
    fun `forced dependency bypasses ordinary automatic preference`() {
        val dependency = ExternalPluginDependency.builder("Legacy", "2.0.0")
            .artifact("https://github.com/example/Legacy.jar", 128, "a".repeat(64))
            .downloadPolicy(DownloadPolicy.FORCED)
            .build()

        assertEquals(DownloadDecision.Allow,
            policy.decide(dependency, configuration(automatic = false, allowExternal = true, allowNew = true)))
    }

    @Test
    fun `forced dependency cannot bypass host allow list`() {
        val dependency = ExternalPluginDependency.builder("Legacy", "2.0.0")
            .artifact("https://evil.example/Legacy.jar", 128, "a".repeat(64))
            .downloadPolicy(DownloadPolicy.FORCED)
            .build()

        assertTrue(policy.decide(dependency,
            configuration(automatic = true, allowExternal = true, allowNew = true)) is DownloadDecision.Blocked)
    }

    @Test
    fun `manual page remains manual when automatic downloads are enabled`() {
        val dependency = Dependencies.plugin("LuckPerms", "5.4.0", "https://luckperms.net/download")
        assertEquals(DownloadDecision.Manual,
            policy.decide(dependency, configuration(automatic = true, allowExternal = true, allowNew = true)))
    }

    private fun configuration(automatic: Boolean, allowExternal: Boolean, allowNew: Boolean) =
        UpdateConfiguration(
            downloads = UpdateConfiguration.Downloads(
                automatic = automatic,
                allowExternalUrls = allowExternal,
                allowedHosts = setOf("github.com"),
            ),
            installation = UpdateConfiguration.Installation(allowNewPlugins = allowNew),
        )
}
