package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.core.metrics.MetricsRegistry
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter

/** Starts the built-in runtime telemetry without coupling the composition root to its details. */
internal class RuntimeMetricsBootstrap(
    private val registry: MetricsRegistry,
    private val platform: PlatformAdapter,
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
