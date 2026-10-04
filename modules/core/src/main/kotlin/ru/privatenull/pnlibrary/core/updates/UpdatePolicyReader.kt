package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import java.time.Duration
import java.time.Instant

/** Converts the current `updates` YAML section into a validated policy model. */
internal class UpdatePolicyReader(
    private val malformedValue: () -> Unit,
) {
    fun read(root: Map<String, Any?>): UpdateConfiguration {
        val defaults = UpdateConfiguration()
        val plugins = PluginPolicySchema.from(root)
        return UpdateConfiguration(
            enabled = boolean(root, "enabled", defaults.enabled),
            checks = defaults.checks,
            notifications = notifications(root.child("notifications"), defaults.notifications),
            downloads = downloads(root.child("downloads")),
            installation = installation(root.child("installation"), defaults.installation),
            safety = safety(root.child("safety"), defaults.safety),
            library = library(root.child("library"), defaults.library),
            pluginUpdatesEnabled = if (plugins.nested) boolean(plugins.section, "enabled", true) else true,
            pluginAutomaticDownload = if (plugins.nested) {
                boolean(plugins.section, "automatic-download", false)
            } else {
                false
            },
            plugins = plugins.values.mapNotNull { (id, value) -> plugin(id, value, plugins) }.toMap(),
        )
    }

    private fun notifications(
        values: Map<String, Any?>?,
        defaults: UpdateConfiguration.Notifications,
    ) = UpdateConfiguration.Notifications(
        console = boolean(values, "console", true),
        administrators = boolean(values, "administrators", true),
        repeatInterval = duration(values, "repeat-interval", defaults.repeatInterval),
        permission = text(values, "permission", defaults.permission),
        operators = boolean(values, "operators", true),
    )

    private fun downloads(values: Map<String, Any?>?) = UpdateConfiguration.Downloads(
        automatic = boolean(values, "automatic", false),
        allowManagedPlugins = boolean(values, "allow-managed-plugins", true),
        allowExternalUrls = boolean(values, "allow-external-urls", false),
    )

    private fun installation(
        values: Map<String, Any?>?,
        defaults: UpdateConfiguration.Installation,
    ) = UpdateConfiguration.Installation(
        allowNewPlugins = boolean(values, "allow-new-plugins", false),
        requireSha256 = boolean(values, "require-sha256", true),
        restartAfterConfirmation = boolean(values, "restart-after-confirmation", false),
        restartCommand = text(values, "restart-command", defaults.restartCommand),
    )

    private fun safety(
        values: Map<String, Any?>?,
        defaults: UpdateConfiguration.Safety,
    ): UpdateConfiguration.Safety {
        val maximumOnline = (values?.get("maximum-online-for-one-click") as? Number)
            ?.toInt()
            ?.takeIf { count -> count >= 0 }
            ?: defaults.maximumOnlineForOneClick.also {
                if (values?.containsKey("maximum-online-for-one-click") == true) malformedValue()
            }
        return UpdateConfiguration.Safety(
            maximumOnlineForOneClick = maximumOnline,
            requireSecondConfirmationAboveLimit = boolean(
                values,
                "require-second-confirmation-above-limit",
                true,
            ),
        )
    }

    private fun library(
        values: Map<String, Any?>?,
        defaults: UpdateConfiguration.LibraryPolicy,
    ) = UpdateConfiguration.LibraryPolicy(
        channel = channel(values?.get("channel"), defaults.channel) ?: defaults.channel,
        automaticDownload = boolean(values, "automatic-download", false),
    )

    private fun plugin(
        rawId: String,
        rawValue: Any?,
        schema: PluginPolicySchema,
    ): Pair<String, UpdateConfiguration.PluginPolicy>? {
        if (schema.nested && rawId in RESERVED_PLUGIN_KEYS) return null

        val id = rawId.trim().lowercase()
        val values = rawValue.asStringMap()
        if (!PRODUCT_ID.matches(id) || values == null) return invalid(null)

        return id to UpdateConfiguration.PluginPolicy(
            channel = channel(values["channel"], null),
            enabled = pluginEnabled(values),
            automaticDownload = pluginAutomaticDownload(values),
            pauseUntil = pluginPause(values),
            disabledModules = disabledModules(values),
        )
    }

    private fun pluginEnabled(values: Map<String, Any?>): Boolean =
        when (val mode = values["mode"] ?: values["update"]) {
            null -> values["enabled"]?.let { enabled ->
                if (enabled is Boolean) enabled else invalid(true)
            } ?: true
            is String -> when (mode.trim().lowercase()) {
                "enabled", "normal", "paused" -> true
                "disabled" -> false
                else -> invalid(true)
            }
            else -> invalid(true)
        }

    private fun pluginAutomaticDownload(values: Map<String, Any?>): Boolean =
        (values["automatic-download"] ?: values["automatic"])?.let { automatic ->
            if (automatic is Boolean) automatic else invalid(false)
        } ?: false

    private fun pluginPause(values: Map<String, Any?>): Instant? {
        (values["pause-until"] as? String)?.let { rawDeadline ->
            val deadline = runCatching { Instant.parse(rawDeadline) }.getOrNull() ?: return invalid(null)
            val remaining = Duration.between(Instant.now(), deadline)
            return deadline.takeIf {
                !remaining.isNegative && !remaining.isZero && remaining <= MAXIMUM_PAUSE
            } ?: invalid(null)
        }

        val rawDuration = values["pause"] as? String ?: return null
        val pause = parseDuration(rawDuration) ?: return invalid(null)
        return if (pause <= MAXIMUM_PAUSE) Instant.now().plus(pause) else invalid(null)
    }

    private fun disabledModules(values: Map<String, Any?>): Set<String> =
        (values["modules"] as? Map<*, *>)?.entries
            ?.filter { (_, state) -> state == false || state.toString().equals("disabled", true) }
            ?.map { (module, _) -> module.toString().lowercase() }
            ?.toSet()
            .orEmpty()

    private fun channel(value: Any?, default: UpdateChannel?): UpdateChannel? {
        if (value == null) return default
        if (value !is String) return invalid(default)
        return runCatching { UpdateChannel.valueOf(value.trim().uppercase()) }
            .getOrElse { invalid(default) }
    }

    private fun boolean(values: Map<String, Any?>?, key: String, default: Boolean): Boolean {
        val value = values?.get(key) ?: return default
        return if (value is Boolean) value else invalid(default)
    }

    private fun text(values: Map<String, Any?>?, key: String, default: String): String {
        val value = values?.get(key) as? String
        if (!value.isNullOrBlank()) return value
        if (values?.containsKey(key) == true) malformedValue()
        return default
    }

    private fun duration(values: Map<String, Any?>?, key: String, default: Duration): Duration {
        val raw = values?.get(key) as? String
            ?: return if (values?.containsKey(key) == true) invalid(default) else default
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

    private fun <T> invalid(fallback: T): T {
        malformedValue()
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

    private companion object {
        val DURATION_PATTERN = Regex("([1-9][0-9]*)([mhd])")
        val PRODUCT_ID = Regex("[a-z0-9][a-z0-9_.-]*")
        val MAXIMUM_PAUSE: Duration = Duration.ofDays(7)
        val RESERVED_PLUGIN_KEYS = setOf("enabled", "automatic-download", "plugins", "policies")

        fun Map<String, Any?>.child(key: String): Map<String, Any?>? = get(key).asStringMap()

        fun Any?.asStringMap(): Map<String, Any?>? =
            (this as? Map<*, *>)?.entries?.associate { (key, value) -> key.toString() to value }
    }
}
