package org.pnfolder.pnlibrary.core.metrics.bstats

import org.bstats.config.MetricsConfig
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

/**
 * What the `bStats` folder next to the plugins says about sending statistics.
 *
 * Every plugin of the server reads the same file, so the owner of the server opts out once for all of them. A file that
 * cannot be read counts as an opt-out: without knowing the answer nothing is sent.
 *
 * @property enabled whether statistics may be sent
 * @property serverUuid anonymous identifier of this server, the same for every plugin
 * @property logFailedRequests whether failed requests are written to the log
 * @property logSentData whether the data that is sent is written to the log
 * @property logResponseStatusText whether the answer of bStats is written to the log
 * @property createdNow whether this call created the file, so a platform may show a short notice about it
 */
class BStatsSettings(
    val enabled: Boolean,
    val serverUuid: String,
    val logFailedRequests: Boolean = false,
    val logSentData: Boolean = false,
    val logResponseStatusText: Boolean = false,
    val createdNow: Boolean = false,
) {
    /** Reading and writing of the shared file in the two layouts that plugins use. */
    companion object {
        private const val HEADER = """# bStats (https://bStats.org) collects some basic information for plugin authors, like how
# many people use their plugin and their total player count. It's recommended to keep bStats
# enabled, but if you're not comfortable with this, you can turn this setting off. There is no
# performance penalty associated with having metrics enabled, and data sent to bStats is fully
# anonymous."""

        /** Settings that send nothing; used when the file cannot be read or written at all. */
        @JvmStatic
        fun disabled(): BStatsSettings = BStatsSettings(enabled = false, serverUuid = UUID.randomUUID().toString())

        /**
         * Reads `bStats/config.yml`, the layout Bukkit and BungeeCord plugins share, and creates it when it is missing
         * or has no server identifier. Values already in the file, such as an opt-out, are kept.
         */
        @JvmStatic
        fun yaml(file: File): BStatsSettings {
            val existing = if (file.exists()) {
                try {
                    parse(file.readLines())
                } catch (_: IOException) {
                    return disabled()
                }
            } else {
                emptyMap()
            }
            val uuid = existing["serverUuid"]?.takeIf(String::isNotBlank)
            val settings = BStatsSettings(
                enabled = flag(existing["enabled"], true),
                serverUuid = uuid ?: UUID.randomUUID().toString(),
                logFailedRequests = flag(existing["logFailedRequests"], false),
                logSentData = flag(existing["logSentData"], false),
                logResponseStatusText = flag(existing["logResponseStatusText"], false),
                createdNow = !file.exists(),
            )
            if (uuid == null) runCatchingNonFatal { write(file, settings) }
            return settings
        }

        /** Reads `bStats/config.txt`, the layout Velocity plugins share, and creates it when it is missing. */
        @JvmStatic
        fun properties(file: File): BStatsSettings = try {
            MetricsConfig(file, true).let {
                BStatsSettings(
                    enabled = it.isEnabled,
                    serverUuid = it.serverUUID,
                    logFailedRequests = it.isLogErrorsEnabled,
                    logSentData = it.isLogSentDataEnabled,
                    logResponseStatusText = it.isLogResponseStatusTextEnabled,
                    createdNow = !it.didExistBefore(),
                )
            }
        } catch (_: IOException) {
            disabled()
        }

        private fun parse(lines: List<String>): Map<String, String> = lines
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") && ':' in it }
            .associate { line ->
                line.substringBefore(':').trim() to line.substringAfter(':').trim().trim('"', '\'')
            }

        private fun flag(value: String?, default: Boolean): Boolean =
            value?.lowercase(Locale.ROOT)?.let { it == "true" } ?: default

        private fun write(file: File, settings: BStatsSettings) {
            file.parentFile?.mkdirs()
            file.writeText(
                listOf(
                    HEADER,
                    "enabled: ${settings.enabled}",
                    "serverUuid: \"${settings.serverUuid}\"",
                    "logFailedRequests: ${settings.logFailedRequests}",
                    "logSentData: ${settings.logSentData}",
                    "logResponseStatusText: ${settings.logResponseStatusText}",
                ).joinToString(System.lineSeparator(), postfix = System.lineSeparator()),
            )
        }
    }
}
