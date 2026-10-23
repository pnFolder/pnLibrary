package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.core.metrics.ProviderMetricsFactory
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsMetricsSession
import org.slf4j.Logger
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path

/** Creates bStats and FastStats sessions for Velocity using explicit platform adapters. */
internal class VelocityMetricsFactory(
    private val server: ProxyServer,
    private val logger: Logger,
    private val dataDirectory: Path,
) : ProviderMetricsFactory() {

    override fun openBStats(owner: Any, projectId: Int): PluginMetrics {
        val client = VelocityBStats.open(owner, projectId, server, logger, dataDirectory)
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
                .invoke(provider, owner, server, logger, dataDirectory, token) as PluginMetrics
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    private companion object {
        const val FASTSTATS_PROVIDER = "org.pnfolder.pnlibrary.velocity.faststats.FastStatsVelocityProvider"
    }
}
