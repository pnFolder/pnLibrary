package ru.privatenull.pnlibrary.core.updates

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant

/** Reads, migrates and validates the persisted update policy. */
internal class UpdateConfigurationLoader(
    private val warning: (String) -> Unit,
) {
    private var malformed = false

    fun load(file: Path): UpdateConfiguration {
        Files.createDirectories(file.toAbsolutePath().parent)
        if (!Files.exists(file)) {
            val defaults = UpdateConfiguration()
            writeAtomic(file, defaults.toYaml())
            return defaults
        }

        val source = Files.readString(file, StandardCharsets.UTF_8)
        val parsed = parseYaml(source)
        migrateLegacyConfiguration(file, parsed)?.let { return it }
        return readCurrentConfiguration(file, source, parsed)
    }

    private fun migrateLegacyConfiguration(
        file: Path,
        parsed: Map<String, Any?>,
    ): UpdateConfiguration? {
        if (parsed.containsKey("updates")) return null
        if (!parsed.containsKey("channel") && !parsed.containsKey("auto-download")) return null

        val channel = (parsed["channel"] as? String)
            ?.lowercase()
            ?.takeIf(ALLOWED_CHANNELS::contains)
            ?: "stable"
        val migrated = UpdateConfiguration(
            downloads = UpdateConfiguration.Downloads(automatic = parsed["auto-download"] == true),
            legacyChannel = channel,
        )
        val backup = file.resolveSibling("${file.fileName}.pre-orchestrator.bak")
        if (!Files.exists(backup)) Files.copy(file, backup)
        writeAtomic(file, migrated.toYaml())
        return migrated
    }

    private fun readCurrentConfiguration(
        file: Path,
        source: String,
        parsed: Map<String, Any?>,
    ): UpdateConfiguration {
        val root = parsed.child("updates") ?: invalid(emptyMap())
        ensureLibrarySection(file, source, parsed, root.child("library"))
        val defaults = UpdateConfiguration()
        val pluginSchema = PluginPolicySchema.from(root)

        val result = UpdateConfiguration(
            enabled = boolean(root, "enabled", defaults.enabled),
            checks = defaults.checks,
            notifications = readNotifications(root.child("notifications"), defaults.notifications),
            downloads = readDownloads(root.child("downloads")),
            installation = readInstallation(root.child("installation"), defaults.installation),
            safety = readSafety(root.child("safety"), defaults.safety),
            library = readLibraryPolicy(root.child("library"), defaults.library),
            pluginUpdatesEnabled = if (pluginSchema.nested) boolean(pluginSchema.section, "enabled", true) else true,
            pluginAutomaticDownload = if (pluginSchema.nested) {
                boolean(pluginSchema.section, "automatic-download", false)
            } else false,
            plugins = pluginSchema.values.mapNotNull { (id, value) -> readPluginPolicy(id, value, pluginSchema) }.toMap(),
        )
        if (malformed) warning("Invalid update configuration values were replaced with conservative defaults")
        return result
    }

    private fun readNotifications(map: Map<String, Any?>?, defaults: UpdateConfiguration.Notifications) =
        UpdateConfiguration.Notifications(
            console = boolean(map, "console", true),
            administrators = boolean(map, "administrators", true),
            repeatInterval = duration(map, "repeat-interval", defaults.repeatInterval),
            permission = text(map, "permission", defaults.permission),
            operators = boolean(map, "operators", true),
        )

    private fun readDownloads(map: Map<String, Any?>?) = UpdateConfiguration.Downloads(
        automatic = boolean(map, "automatic", false),
        allowManagedPlugins = boolean(map, "allow-managed-plugins", true),
        allowExternalUrls = boolean(map, "allow-external-urls", false),
    )

    private fun readInstallation(
        map: Map<String, Any?>?,
        defaults: UpdateConfiguration.Installation,
    ) = UpdateConfiguration.Installation(
        allowNewPlugins = boolean(map, "allow-new-plugins", false),
        requireSha256 = boolean(map, "require-sha256", true),
        restartAfterConfirmation = boolean(map, "restart-after-confirmation", false),
        restartCommand = text(map, "restart-command", defaults.restartCommand),
    )

    private fun readSafety(map: Map<String, Any?>?, defaults: UpdateConfiguration.Safety): UpdateConfiguration.Safety {
        val maximumOnline = (map?.get("maximum-online-for-one-click") as? Number)
            ?.toInt()
            ?.takeIf { it >= 0 }
            ?: defaults.maximumOnlineForOneClick.also {
                if (map?.containsKey("maximum-online-for-one-click") == true) markMalformed()
            }
        return UpdateConfiguration.Safety(
            maximumOnlineForOneClick = maximumOnline,
            requireSecondConfirmationAboveLimit = boolean(map, "require-second-confirmation-above-limit", true),
        )
    }

    private fun readLibraryPolicy(
        map: Map<String, Any?>?,
        defaults: UpdateConfiguration.LibraryPolicy,
    ) = UpdateConfiguration.LibraryPolicy(
        channel = channel(map?.get("channel"), defaults.channel) ?: defaults.channel,
        automaticDownload = boolean(map, "automatic-download", false),
    )

    private fun readPluginPolicy(
        rawId: String,
        rawValue: Any?,
        schema: PluginPolicySchema,
    ): Pair<String, UpdateConfiguration.PluginPolicy>? {
        if (schema.nested && rawId in RESERVED_PLUGIN_KEYS) return null
        val id = rawId.trim().lowercase()
        val policy = rawValue.asStringMap()
        if (!PRODUCT_ID.matches(id) || policy == null) return invalid(null)

        return id to UpdateConfiguration.PluginPolicy(
            channel = channel(policy["channel"], null),
            enabled = pluginEnabled(policy),
            automaticDownload = pluginAutomaticDownload(policy),
            pauseUntil = pluginPause(policy),
            disabledModules = disabledModules(policy),
        )
    }

    private fun pluginEnabled(policy: Map<String, Any?>): Boolean = when (val mode = policy["mode"] ?: policy["update"]) {
        null -> policy["enabled"]?.let { if (it is Boolean) it else invalid(true) } ?: true
        is String -> when (mode.trim().lowercase()) {
            "enabled", "normal", "paused" -> true
            "disabled" -> false
            else -> invalid(true)
        }
        else -> invalid(true)
    }

    private fun pluginAutomaticDownload(policy: Map<String, Any?>): Boolean =
        (policy["automatic-download"] ?: policy["automatic"])?.let {
            if (it is Boolean) it else invalid(false)
        } ?: false

    private fun pluginPause(policy: Map<String, Any?>): Instant? {
        (policy["pause-until"] as? String)?.let { rawDeadline ->
            val deadline = runCatching { Instant.parse(rawDeadline) }.getOrNull() ?: return invalid(null)
            val remaining = Duration.between(Instant.now(), deadline)
            return deadline.takeIf { !remaining.isNegative && !remaining.isZero && remaining <= MAXIMUM_PAUSE }
                ?: invalid(null)
        }
        val rawDuration = policy["pause"] as? String ?: return null
        val pause = parseDuration(rawDuration) ?: return invalid(null)
        return if (pause <= MAXIMUM_PAUSE) Instant.now().plus(pause) else invalid(null)
    }

    private fun disabledModules(policy: Map<String, Any?>): Set<String> =
        (policy["modules"] as? Map<*, *>)?.entries
            ?.filter { (_, value) -> value == false || value.toString().equals("disabled", true) }
            ?.map { (module, _) -> module.toString().lowercase() }
            ?.toSet()
            .orEmpty()

    private fun channel(value: Any?, default: UpdateChannel?): UpdateChannel? {
        if (value == null) return default
        if (value !is String) return invalid(default)
        return runCatching { UpdateChannel.valueOf(value.trim().uppercase()) }.getOrElse { invalid(default) }
    }

    private fun boolean(map: Map<String, Any?>?, key: String, default: Boolean): Boolean {
        val value = map?.get(key) ?: return default
        return if (value is Boolean) value else invalid(default)
    }

    private fun text(map: Map<String, Any?>?, key: String, default: String): String {
        val value = map?.get(key) as? String
        return if (!value.isNullOrBlank()) value else {
            if (map?.containsKey(key) == true) markMalformed()
            default
        }
    }

    private fun duration(map: Map<String, Any?>?, key: String, default: Duration): Duration {
        val raw = map?.get(key) as? String
            ?: return if (map?.containsKey(key) == true) invalid(default) else default
        return parseDuration(raw) ?: invalid(default)
    }

    private fun parseDuration(value: String): Duration? {
        val match = DURATION_PATTERN.matchEntire(value.trim().lowercase()) ?: return null
        val amount = match.groupValues[1].toLong()
        return when (match.groupValues[2]) {
            "m" -> Duration.ofMinutes(amount)
            "h" -> Duration.ofHours(amount)
            else -> Duration.ofDays(amount)
        }
    }

    private fun ensureLibrarySection(
        file: Path,
        source: String,
        parsed: Map<String, Any?>,
        library: Map<String, Any?>?,
    ) {
        if (library != null || !parsed.containsKey("updates") || source.contains("  library:\n")) return
        val block = """

  library:
    # Канал релизов для самой pnLibrary: stable, rc, beta, alpha или dev.
    channel: stable
    # Разрешить автоматическую загрузку новой версии самой pnLibrary.
    automatic-download: false
        """.trimEnd()
        runCatching { writeAtomic(file, source.trimEnd() + block + "\n") }
    }

    private fun markMalformed() {
        malformed = true
    }

    private fun <T> invalid(fallback: T): T {
        markMalformed()
        return fallback
    }

    private data class PluginPolicySchema(
        val section: Map<String, Any?>?,
        val values: Map<String, Any?>,
        val nested: Boolean,
    ) {
        companion object {
            fun from(root: Map<String, Any?>): PluginPolicySchema {
                val section = root.child("plugins")
                val policies = section?.child("policies")
                val plugins = section?.child("plugins")
                val nested = policies != null || plugins != null
                val values = policies ?: plugins ?: section
                    ?.takeIf { map -> map.keys.none(RESERVED_PLUGIN_KEYS::contains) }
                    ?: root.child("components")
                    ?: emptyMap()
                return PluginPolicySchema(section, values, nested)
            }
        }
    }

    companion object {
        private val DURATION_PATTERN = Regex("([1-9][0-9]*)([mhd])")
        private val PRODUCT_ID = Regex("[a-z0-9][a-z0-9_.-]*")
        private val MAXIMUM_PAUSE: Duration = Duration.ofDays(7)
        private val ALLOWED_CHANNELS = setOf("stable", "rc", "beta", "alpha", "dev")
        private val RESERVED_PLUGIN_KEYS = setOf("enabled", "automatic-download", "plugins", "policies")

        @Suppress("UNCHECKED_CAST")
        private fun parseYaml(source: String): Map<String, Any?> = try {
            val strictBooleans = source.replace(
                Regex("(?i)(:\\s*)(yes|no|on|off)(?=\\s*[,}#\\r\\n])"),
                "$1\"$2\"",
            )
            (Yaml(SafeConstructor(LoaderOptions())).load<Any?>(strictBooleans) as? Map<*, *>)
                ?.entries
                ?.associate { it.key.toString() to it.value }
                ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }

        private fun Map<String, Any?>.child(key: String): Map<String, Any?>? = get(key).asStringMap()

        private fun Any?.asStringMap(): Map<String, Any?>? =
            (this as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }

        private fun writeAtomic(file: Path, text: String) {
            val temporary = Files.createTempFile(file.toAbsolutePath().parent, file.fileName.toString(), ".tmp")
            try {
                Files.writeString(temporary, text, StandardCharsets.UTF_8)
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }
}
