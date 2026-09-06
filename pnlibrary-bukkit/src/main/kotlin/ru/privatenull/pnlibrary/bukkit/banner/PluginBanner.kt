package ru.privatenull.pnlibrary.bukkit.banner

import org.bukkit.ChatColor
import org.bukkit.plugin.java.JavaPlugin
import java.time.Duration
import java.util.Collections
import java.util.Objects
import java.util.regex.Pattern

/**
 * Console banner builder and update checker for Bukkit plugins.
 */
class PluginBanner private constructor() {

    enum class Status(val displayName: String, val color: ChatColor) {
        OK("OK", ChatColor.GREEN),
        WARN("WARN", ChatColor.GOLD),
        FAIL("FAIL", ChatColor.RED),
        SKIP("SKIP", ChatColor.GRAY),
    }

    data class Entry @JvmOverloads constructor(
        val status: Status,
        val details: String? = null
    )

    data class GitHubRepository(val owner: String, val repository: String) {
        init {
            require(owner.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid GitHub owner: $owner" }
            require(repository.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid GitHub repository: $repository" }
        }

        fun apiUrl(): String = "https://api.github.com/repos/$owner/$repository/releases/latest"
        fun releasesUrl(): String = "https://github.com/$owner/$repository/releases/latest"
    }

    class Identity(
        val plugin: JavaPlugin,
        val developer: String
    ) {
        init {
            require(developer.isNotBlank()) { "developer cannot be blank" }
        }

        var github: GitHubRepository? = null
            private set
        var showUpToDateMessage: Boolean = false
        var showUpdateErrors: Boolean = true
        var autoDownloadUpdates: Boolean = false
        var notifyAdministrators: Boolean = false
        var notifyOnlineAdministrators: Boolean = true
        var notifyAdministratorsOnJoin: Boolean = true
        var notificationPermission: String? = null
        var supportUrl: String? = null
        var bStatsPluginId: Int? = null
        var updateTimeout: Duration = Duration.ofSeconds(15)
        var updateCheckInterval: Duration = Duration.ofHours(6)
        var maxUpdateSizeBytes: Long = 100L * 1024L * 1024L
        var updateAssetPattern: Pattern = Pattern.compile("(?i)^(?!.*(?:sources|javadoc)).*\\.jar$")

        fun github(owner: String, repository: String): Identity = apply {
            github = GitHubRepository(owner, repository)
        }

        fun bStats(pluginId: Int): Identity = apply {
            require(pluginId > 0) { "bStats plugin id must be positive" }
            bStatsPluginId = pluginId
        }

        fun showUpToDateMessage(value: Boolean): Identity = apply { showUpToDateMessage = value }
        fun showUpdateErrors(value: Boolean): Identity = apply { showUpdateErrors = value }
        fun autoDownloadUpdates(value: Boolean): Identity = apply { autoDownloadUpdates = value }
        fun notifyAdministrators(value: Boolean): Identity = apply { notifyAdministrators = value }
        fun notifyOnlineAdministrators(value: Boolean): Identity = apply { notifyOnlineAdministrators = value }
        fun notifyAdministratorsOnJoin(value: Boolean): Identity = apply { notifyAdministratorsOnJoin = value }

        fun notificationPermission(permission: String): Identity = apply {
            require(permission.isNotBlank()) { "notification permission cannot be blank" }
            notificationPermission = permission
        }

        fun supportUrl(url: String): Identity = apply {
            require(url.matches(Regex("https?://.+"))) { "support URL must use HTTP or HTTPS" }
            supportUrl = url
        }
    }

    class Data(val identity: Identity) {
        constructor(plugin: JavaPlugin, developer: String) : this(Identity(plugin, developer))

        private val _entries = linkedMapOf<String, Entry>()
        val entries: Map<String, Entry> get() = Collections.unmodifiableMap(_entries)

        fun github(owner: String, repository: String): Data = apply { identity.github(owner, repository) }
        fun showUpToDateMessage(value: Boolean): Data = apply { identity.showUpToDateMessage(value) }

        fun component(component: String, status: Status, details: String? = null): Data = apply {
            require(component.isNotBlank()) { "component cannot be blank" }
            _entries[component.trim()] = Entry(status, details?.takeIf { it.isNotBlank() }?.trim())
        }

        fun ok(component: String, details: String? = null): Data = component(component, Status.OK, details)
        fun warn(component: String, details: String? = null): Data = component(component, Status.WARN, details)
        fun fail(component: String, details: String? = null): Data = component(component, Status.FAIL, details)
        fun skip(component: String, details: String? = null): Data = component(component, Status.SKIP, details)

        val plugin: JavaPlugin get() = identity.plugin
        val developer: String get() = identity.developer
    }

    companion object {
        @JvmStatic
        fun broadcastEnable(data: Data) {
            val console = data.plugin.server.consoleSender
            val statusColor = if (data.entries.values.any { it.status == Status.FAIL }) ChatColor.RED
                              else if (data.entries.values.any { it.status == Status.WARN }) ChatColor.GOLD
                              else ChatColor.GREEN

            console.sendMessage("")
            console.sendMessage("${statusColor}/\\_/\\")
            console.sendMessage("${statusColor}( ^.^ )   ${ChatColor.AQUA}${data.plugin.name} ${ChatColor.WHITE}v${data.plugin.description.version} ${ChatColor.DARK_GRAY}by ${ChatColor.LIGHT_PURPLE}${data.developer}")
            console.sendMessage("${statusColor}> ^ <")
            console.sendMessage("")

            for ((comp, entry) in data.entries) {
                console.sendMessage("${ChatColor.DARK_GRAY}          > ${ChatColor.WHITE}$comp ${ChatColor.DARK_GRAY}  ${entry.status.color}[ ${entry.status.displayName} ]")
                if (!entry.details.isNullOrBlank()) {
                    console.sendMessage("${ChatColor.DARK_GRAY}            └ ${ChatColor.GRAY}${entry.details}")
                }
            }
            console.sendMessage("")
        }

        @JvmStatic
        fun broadcastDisable(data: Data) {
            val console = data.plugin.server.consoleSender
            console.sendMessage("${ChatColor.RED}/\\_/\\")
            console.sendMessage("${ChatColor.RED}( -.- )   ${ChatColor.AQUA}${data.plugin.name} ${ChatColor.WHITE}отключён")
            console.sendMessage("${ChatColor.RED}> ^ <")
        }
    }
}
