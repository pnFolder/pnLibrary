package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration

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
