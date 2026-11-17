package org.pnfolder.pnlibrary.core.metrics.bstats

import org.bstats.MetricsBase
import org.bstats.charts.CustomChart
import org.bstats.json.JsonObjectBuilder
import java.util.function.Consumer

/** The one bStats sender for every platform. Collecting charts, timing and submitting stay in [MetricsBase]. */
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
        { builder ->
            appendPlatformData(builder)
            builder.appendField("javaVersion", System.getProperty("java.version"))
            builder.appendField("osName", System.getProperty("os.name"))
            builder.appendField("osArch", System.getProperty("os.arch"))
            builder.appendField("osVersion", System.getProperty("os.version"))
            builder.appendField("coreCount", Runtime.getRuntime().availableProcessors())
        },
        { builder -> builder.appendField("pluginVersion", pluginVersion) },
        mainThread?.let { run -> Consumer<Runnable> { task -> run(task) } },
        { ownerEnabled() },
        { message, error -> warn(message, error) },
        { message -> info(message) },
        settings.logFailedRequests,
        settings.logSentData,
        settings.logResponseStatusText,
        false,
    )

    fun addCustomChart(chart: CustomChart) = base.addCustomChart(chart)

    fun shutdown() = base.shutdown()
}
