package ru.privatenull.pnlibrary.core.downloads

import ru.privatenull.pnlibrary.api.plugin.DownloadPolicy
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.core.updates.UpdateConfiguration

internal sealed class DownloadDecision {
    data object Allow : DownloadDecision()
    data object Manual : DownloadDecision()
    data class Blocked(val reason: String) : DownloadDecision()
}

/** Applies administrator security controls before a module's download preference. */
internal class DependencyDownloadPolicy {
    fun decide(dependency: PluginDependency, configuration: UpdateConfiguration): DownloadDecision {
        if (dependency.downloadPolicy == DownloadPolicy.MANUAL) return DownloadDecision.Manual
        if (!configuration.enabled) return DownloadDecision.Blocked("update subsystem is disabled")
        if (!configuration.installation.allowNewPlugins) {
            return DownloadDecision.Blocked("installing new plugins is disabled")
        }

        dependency.managed?.let {
            if (!configuration.downloads.allowManagedPlugins) {
                return DownloadDecision.Blocked("managed plugin downloads are disabled")
            }
        }
        dependency.external?.let { external ->
            val artifact = external.artifact
                ?: return DownloadDecision.Blocked("automatic external download requires an exact artifact")
            if (!configuration.downloads.allowExternalUrls) {
                return DownloadDecision.Blocked("external URL downloads are disabled")
            }
            if (!artifact.uri.scheme.equals("https", true)) {
                return DownloadDecision.Blocked("external artifact must use HTTPS")
            }
            if (artifact.uri.host?.lowercase(java.util.Locale.ROOT) !in configuration.downloads.allowedHosts) {
                return DownloadDecision.Blocked("external artifact host is not allow-listed")
            }
            if (configuration.installation.requireSha256 &&
                artifact.sha256?.matches(Regex("[0-9a-fA-F]{64}")) != true) {
                return DownloadDecision.Blocked("external artifact requires SHA-256")
            }
        }

        return when (dependency.downloadPolicy) {
            DownloadPolicy.MANUAL -> DownloadDecision.Manual
            DownloadPolicy.AUTOMATIC -> if (configuration.downloads.automatic) {
                DownloadDecision.Allow
            } else {
                DownloadDecision.Manual
            }
            DownloadPolicy.FORCED -> DownloadDecision.Allow
        }
    }
}
