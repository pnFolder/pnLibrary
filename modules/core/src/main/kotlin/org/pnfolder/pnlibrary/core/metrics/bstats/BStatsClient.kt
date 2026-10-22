package org.pnfolder.pnlibrary.core.metrics.bstats

import org.bstats.MetricsBase
import org.bstats.charts.CustomChart
import org.bstats.json.JsonObjectBuilder
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * The one bStats sender for every platform.
 *
 * Collecting charts, timing and submitting stay in [MetricsBase], which must not be changed. A platform only says what
 * differs between servers: its name, where the settings are, what it reports about itself, and how to reach the main
 * thread and the log.
 *
 * @param platform the platform name bStats knows: `bukkit`, `bungeecord` or `velocity`
 * @param serviceId the bStats identifier of the plugin
 * @param settings what the shared bStats file says
 * @param pluginVersion version of the plugin that reports
 * @param appendPlatformData adds what the platform reports about itself: players, online mode, version
 * @param mainThread runs a task on the server thread, or `null` when data may be collected on any thread
 * @param ownerEnabled whether the plugin is still running; sending stops once it is not
 * @param warn writes a warning with the cause to the plugin log
 * @param info writes a line to the plugin log
 */
class BStatsClient(
    platform: String,
    serviceId: Int,
    settings: BStatsSettings,
    pluginVersion: String,
    appendPlatformData: (JsonObjectBuilder) -> Unit,
    mainThread: ((Runnable) -> Unit)? = null,
    ownerEnabled: () -> Boolean = { true },
    warn: (String, Throwable?) -> Unit,
    info: (String) -> Unit,
) {
    private val base = MetricsBase(
        platform,
        settings.serverUuid,
        serviceId,
        settings.enabled,
        Consumer { builder ->
            appendPlatformData(builder)
            builder.appendField("javaVersion", System.getProperty("java.version"))
            builder.appendField("osName", System.getProperty("os.name"))
            builder.appendField("osArch", System.getProperty("os.arch"))
            builder.appendField("osVersion", System.getProperty("os.version"))
            builder.appendField("coreCount", Runtime.getRuntime().availableProcessors())
        },
        Consumer { builder -> builder.appendField("pluginVersion", pluginVersion) },
        mainThread?.let { run -> Consumer<Runnable> { task -> run(task) } },
        Supplier { ownerEnabled() },
        BiConsumer { message, error -> warn(message, error) },
        Consumer { message -> info(message) },
        settings.logFailedRequests,
        settings.logSentData,
        settings.logResponseStatusText,
        false,
    )

    /** Adds a chart that is read each time data is sent. */
    fun addCustomChart(chart: CustomChart) {
        base.addCustomChart(chart)
    }

    /** Stops sending. */
    fun shutdown() {
        base.shutdown()
    }
}
