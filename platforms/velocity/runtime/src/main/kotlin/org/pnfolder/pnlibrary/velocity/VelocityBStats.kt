package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsClient
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsSettings
import org.slf4j.Logger
import java.nio.file.Path

/** What bStats needs to know about a Velocity proxy; the sending itself is shared by every platform. */
internal object VelocityBStats {
    /** Opens the sender for the plugin instance [owner], which is reported under the bStats [serviceId]. */
    fun open(owner: Any, serviceId: Int, server: ProxyServer, logger: Logger, dataDirectory: Path): BStatsClient {
        val container = server.pluginManager.fromInstance(owner)
            .orElseThrow { IllegalArgumentException("The provided instance is not a plugin") }
        val settings = BStatsSettings.properties(dataDirectory.parent.resolve("bStats").resolve("config.txt").toFile())
        if (settings.createdNow) {
            logger.info("Velocity and some of its plugins collect metrics and send them to bStats (https://bStats.org).")
            logger.info("bStats collects some basic information for plugin authors, like how many people use")
            logger.info("their plugin and their total player count. It's recommend to keep bStats enabled, but")
            logger.info("if you're not comfortable with this, you can opt-out by editing the config.txt file in")
            logger.info("the '/plugins/bStats/' folder and setting enabled to false.")
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
            warn = { message, error -> logger.warn(message, error) },
            info = logger::info,
        )
    }
}
