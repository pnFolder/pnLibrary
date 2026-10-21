package org.pnfolder.pnlibrary.core.runtime.details

import org.pnfolder.pnlibrary.api.metrics.MetricsProvider
import org.pnfolder.pnlibrary.api.metrics.MetricsProviderConfiguration
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.core.metrics.MetricsServiceImpl
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity

/** Starts the built-in runtime telemetry without coupling the composition root to its details. */
internal class RuntimeMetricsBootstrap(
    private val registry: MetricsServiceImpl,
    private val platform: PlatformIdentity,
    private val owner: Any,
    private val warning: (String) -> Unit,
) {
    fun start(): PluginMetrics? {
        var openedSession: PluginMetrics? = null
        return runCatching {
            registry.open(
                owner,
                listOf(
                    MetricsProviderConfiguration(
                        provider = MetricsProvider.FASTSTATS,
                        token = FASTSTATS_TOKEN,
                    ),
                ),
            ).also { metrics ->
                openedSession = metrics
                metrics.simplePie("platform") { platform.type.name.lowercase() }
                metrics.simplePie("implementation") { platform.implementationName }
                metrics.start()
            }
        }.onFailure { error ->
            runCatching { openedSession?.close() }
            warning("FastStats metrics could not be started: ${error.message ?: error.javaClass.simpleName}")
        }.getOrNull()
    }

    private companion object {
        const val FASTSTATS_TOKEN = "009192430b9bfffa176e8cc8dd67cad7"
    }
}
