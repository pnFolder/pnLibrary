package org.pnfolder.pnlibrary.velocity.metrics

import com.velocitypowered.api.proxy.ProxyServer
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.core.metrics.ProviderMetricsFactory
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsClient
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsMetricsSession
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsSettings
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path

/** Creates bStats and FastStats sessions for Velocity using explicit platform adapters. */
internal class VelocityMetricsFactory(
    private val server: ProxyServer,
    private val dataDirectory: Path,
    /** Provides the library logger for user-visible messages; resolved after the host is built. */
    private val pnLogger: () -> PnLogger,
) : ProviderMetricsFactory() {
    private val nativeLogger = LoggerFactory.getLogger(VelocityMetricsFactory::class.java)

    /** Opens a bStats session for the plugin instance [owner] under [projectId]. */
    override fun openBStats(owner: Any, projectId: Int): PluginMetrics {
        val client = openBStats(owner, projectId, server, pnLogger(), dataDirectory)
        return BStatsMetricsSession(projectId, client::addCustomChart, client::shutdown)
    }

    /**
     * FastStats on Velocity is compiled for Java 21. It is loaded by name and never referenced directly, so a
     * Java 17 proxy never links it: the check below rejects the run before the class is touched.
     */
    override fun openFastStats(owner: Any, token: String): PluginMetrics {
        check(Runtime.version().feature() >= 21) {
            "FastStats on Velocity requires Java 21 or newer"
        }
        return try {
            val type = Class.forName(FASTSTATS_PROVIDER, true, javaClass.classLoader)
            val provider = type.getConstructor().newInstance()
            type.getMethod("open", Any::class.java, ProxyServer::class.java, Logger::class.java, Path::class.java, String::class.java)
                .invoke(provider, owner, server, nativeLogger, dataDirectory, token) as PluginMetrics
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    private companion object {
        const val FASTSTATS_PROVIDER = "org.pnfolder.pnlibrary.velocity.faststats.FastStatsVelocityProvider"
    }
}

/** Opens the bStats sender for the plugin instance [owner], which is reported under the bStats [serviceId]; the sending itself is shared by every platform. */
private fun openBStats(owner: Any, serviceId: Int, server: ProxyServer, log: PnLogger, dataDirectory: Path): BStatsClient {
    val container = server.pluginManager.fromInstance(owner)
        .orElseThrow { IllegalArgumentException("The provided instance is not a plugin") }
    val settings = BStatsSettings.properties(dataDirectory.parent.resolve("bStats").resolve("config.txt").toFile())
    if (settings.createdNow) {
        log.info("Velocity and some of its plugins collect metrics and send them to bStats (https://bStats.org).")
        log.info("bStats collects some basic information for plugin authors, like how many people use")
        log.info("their plugin and their total player count. It's recommend to keep bStats enabled, but")
        log.info("if you're not comfortable with this, you can opt-out by editing the config.txt file in")
        log.info("the '/plugins/bStats/' folder and setting enabled to false.")
    }
    return BStatsClient(
        platform = "velocity",
        serviceId = serviceId,
        settings = settings,
        pluginVersion = container.description.version.orElse("unknown"),
        appendPlatformData = { data ->
            data.appendField("playerAmount", server.playerCount)
            data.appendField("managedServers", server.allServers.size)
            data.appendField("onlineMode", if (server.configuration.isOnlineMode) 1 else 0)
            data.appendField("velocityVersionVersion", server.version.version)
            data.appendField("velocityVersionName", server.version.name)
            data.appendField("velocityVersionVendor", server.version.vendor)
        },
        mainThread = { task -> server.scheduler.buildTask(owner, task).schedule() },
        warn = { message, error -> log.warning(message, error) },
        info = log::info,
    )
}
