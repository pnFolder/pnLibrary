package ru.privatenull.pnlibrary.core.updates

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

internal data class UpdateConfiguration(
    val enabled: Boolean = true,
    val checks: Checks = Checks(),
    val notifications: Notifications = Notifications(),
    val downloads: Downloads = Downloads(),
    val installation: Installation = Installation(),
    val safety: Safety = Safety(),
    val legacyChannel: String = "stable",
) {
    data class Checks(val enabled: Boolean = true, val interval: Duration = Duration.ofMinutes(30))
    data class Notifications(
        val console: Boolean = true,
        val administrators: Boolean = true,
        val repeatInterval: Duration = Duration.ofHours(6),
        val permission: String = "pnlibrary.updates.notify",
        val operators: Boolean = true,
    )
    data class Downloads(
        val automatic: Boolean = false,
        val allowManagedPlugins: Boolean = true,
        val allowExternalUrls: Boolean = false,
        val allowedHosts: Set<String> = setOf("github.com", "objects.githubusercontent.com"),
    )
    data class Installation(
        val allowNewPlugins: Boolean = false,
        val requireSha256: Boolean = true,
        val restartAfterConfirmation: Boolean = false,
        val restartCommand: String = "restart",
    )
    data class Safety(
        val maximumOnlineForOneClick: Int = 10,
        val requireSecondConfirmationAboveLimit: Boolean = true,
    )

    val effectiveChecksEnabled get() = enabled && checks.enabled
    val effectiveConsoleNotifications get() = enabled && notifications.console
    val effectiveAdministratorNotifications get() = enabled && notifications.administrators
    val effectiveAutomaticDownloads get() = enabled && downloads.automatic
    val effectiveRestart get() = enabled && installation.restartAfterConfirmation

    companion object {
        private val durationPattern = Regex("([1-9][0-9]*)([mhd])")
        private val hostPattern = Regex("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")

        fun load(file: Path, warning: (String) -> Unit = {}): UpdateConfiguration {
            Files.createDirectories(file.toAbsolutePath().parent)
            if (!Files.exists(file)) {
                writeAtomic(file, DEFAULT_YAML)
                return UpdateConfiguration()
            }
            val source = Files.readString(file, StandardCharsets.UTF_8)
            val parsed = parse(source)
            if (!parsed.containsKey("updates") && (parsed.containsKey("channel") || parsed.containsKey("auto-download"))) {
                val channel = (parsed["channel"] as? String)?.lowercase()
                    ?.takeIf { it in setOf("stable", "beta", "alpha", "dev") } ?: "stable"
                val automatic = parsed["auto-download"] == true
                val backup = file.resolveSibling("${file.fileName}.pre-orchestrator.bak")
                if (!Files.exists(backup)) Files.copy(file, backup)
                val migrated = UpdateConfiguration(downloads = Downloads(automatic = automatic), legacyChannel = channel)
                writeAtomic(file, migrated.toYaml())
                return migrated
            }
            var malformed = false
            fun bad() { malformed = true }
            val root = parsed.map("updates") ?: run { bad(); emptyMap() }
            val defaults = UpdateConfiguration()
            val checks = root.map("checks")
            val notifications = root.map("notifications")
            val downloads = root.map("downloads")
            val installation = root.map("installation")
            val safety = root.map("safety")
            fun bool(map: Map<String, Any?>?, key: String, default: Boolean): Boolean {
                val value = map?.get(key) ?: return default
                return if (value is Boolean) value else { bad(); default }
            }
            fun text(map: Map<String, Any?>?, key: String, default: String): String {
                val value = map?.get(key) as? String
                return if (!value.isNullOrBlank()) value else { if (map?.containsKey(key) == true) bad(); default }
            }
            fun duration(map: Map<String, Any?>?, key: String, default: Duration): Duration {
                val raw = map?.get(key) as? String ?: return if (map?.containsKey(key) == true) { bad(); default } else default
                val match = durationPattern.matchEntire(raw.trim().lowercase()) ?: return run { bad(); default }
                val amount = match.groupValues[1].toLong()
                return when (match.groupValues[2]) { "m" -> Duration.ofMinutes(amount); "h" -> Duration.ofHours(amount); else -> Duration.ofDays(amount) }
            }
            val hosts = (downloads?.get("allowed-hosts") as? List<*>)?.mapNotNull { it as? String }
                ?.map { it.lowercase() }?.takeIf { it.isNotEmpty() && it.all(hostPattern::matches) }?.toSet()
                ?: defaults.downloads.allowedHosts.also { if (downloads?.containsKey("allowed-hosts") == true) bad() }
            val maximumOnline = (safety?.get("maximum-online-for-one-click") as? Number)?.toInt()
                ?.takeIf { it >= 0 } ?: defaults.safety.maximumOnlineForOneClick.also {
                if (safety?.containsKey("maximum-online-for-one-click") == true) bad()
            }
            val result = UpdateConfiguration(
                enabled = bool(root, "enabled", defaults.enabled),
                checks = Checks(bool(checks, "enabled", true), duration(checks, "interval", defaults.checks.interval)),
                notifications = Notifications(
                    bool(notifications, "console", true), bool(notifications, "administrators", true),
                    duration(notifications, "repeat-interval", defaults.notifications.repeatInterval),
                    text(notifications, "permission", defaults.notifications.permission),
                    bool(notifications, "operators", true),
                ),
                downloads = Downloads(
                    bool(downloads, "automatic", false), bool(downloads, "allow-managed-plugins", true),
                    bool(downloads, "allow-external-urls", false), hosts,
                ),
                installation = Installation(
                    bool(installation, "allow-new-plugins", false), bool(installation, "require-sha256", true),
                    bool(installation, "restart-after-confirmation", false),
                    text(installation, "restart-command", defaults.installation.restartCommand),
                ),
                safety = Safety(maximumOnline, bool(safety, "require-second-confirmation-above-limit", true)),
            )
            if (malformed) warning("Invalid update configuration values were replaced with conservative defaults")
            return result
        }

        @Suppress("UNCHECKED_CAST")
        private fun parse(source: String): Map<String, Any?> = try {
            // SnakeYAML's YAML 1.1 resolver treats yes/no/on/off as booleans. Quoting them
            // before parsing keeps security-sensitive flags strict: only true/false enable them.
            val strictBooleans = source.replace(
                Regex("(?i)(:\\s*)(yes|no|on|off)(?=\\s*[,}#\\r\\n])"),
                "$1\"$2\"",
            )
            (Yaml(SafeConstructor(LoaderOptions())).load<Any?>(strictBooleans) as? Map<*, *>)
                ?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
        } catch (_: Exception) { emptyMap() }

        @Suppress("UNCHECKED_CAST")
        private fun Map<String, Any?>.map(key: String): Map<String, Any?>? =
            (get(key) as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }

        private fun writeAtomic(file: Path, text: String) {
            val temporary = Files.createTempFile(file.toAbsolutePath().parent, file.fileName.toString(), ".tmp")
            try {
                Files.writeString(temporary, text, StandardCharsets.UTF_8)
                try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING) }
            } finally { Files.deleteIfExists(temporary) }
        }

        private val DEFAULT_YAML = UpdateConfiguration().toYaml()
    }

    private fun toYaml(): String = """updates:
  enabled: $enabled
  checks:
    enabled: ${checks.enabled}
    interval: ${checks.interval.toMinutes()}m
  notifications:
    console: ${notifications.console}
    administrators: ${notifications.administrators}
    repeat-interval: ${notifications.repeatInterval.toHours()}h
    permission: ${notifications.permission}
    operators: ${notifications.operators}
  downloads:
    automatic: ${downloads.automatic}
    allow-managed-plugins: ${downloads.allowManagedPlugins}
    allow-external-urls: ${downloads.allowExternalUrls}
    allowed-hosts:
${downloads.allowedHosts.joinToString("\n") { "      - $it" }}
  installation:
    allow-new-plugins: ${installation.allowNewPlugins}
    require-sha256: ${installation.requireSha256}
    restart-after-confirmation: ${installation.restartAfterConfirmation}
    restart-command: "${installation.restartCommand.replace("\"", "")}" 
  safety:
    maximum-online-for-one-click: ${safety.maximumOnlineForOneClick}
    require-second-confirmation-above-limit: ${safety.requireSecondConfirmationAboveLimit}
"""
}
