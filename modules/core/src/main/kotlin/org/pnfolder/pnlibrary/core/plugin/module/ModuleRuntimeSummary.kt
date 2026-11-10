package org.pnfolder.pnlibrary.core.plugin.module

import org.pnfolder.pnlibrary.api.metrics.MetricsProvider
import org.pnfolder.pnlibrary.api.metrics.MetricsProviderConfiguration
import org.pnfolder.pnlibrary.api.plugin.PluginMetadata
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration

/** Formats runtime facts of a module into short lines for the startup summary. */
internal object ModuleRuntimeSummary {
    fun platform(metadata: PluginMetadata): String =
        if (metadata.platformImplementation.equals(metadata.platform.displayName, ignoreCase = true)) {
            metadata.platform.displayName
        } else {
            "${metadata.platform.displayName} · ${metadata.platformImplementation}"
        }

    fun metrics(providers: List<MetricsProviderConfiguration>, enabled: Boolean): String? =
        providers.takeIf { it.isNotEmpty() }?.let { configured ->
            "${if (enabled) "enabled" else "disabled"} · ${configured.joinToString(", ") { providerLabel(it) }}"
        }

    private fun providerLabel(configuration: MetricsProviderConfiguration): String = when (configuration.provider) {
        MetricsProvider.BSTATS -> "bStats" + (configuration.projectId?.let { " #$it" } ?: "")
        MetricsProvider.FASTSTATS -> "FastStats"
    }

    fun updates(registration: UpdateRegistration?): String? = registration?.let {
        "${it.snapshot.channel.name} · ${it.repository} · Java ${it.snapshot.requiredJava}+"
    }

    fun placeholderApi(enabled: Boolean, state: String?): String = when {
        !enabled -> "disabled"
        state == null -> "unavailable"
        else -> state.lowercase()
    }
}
