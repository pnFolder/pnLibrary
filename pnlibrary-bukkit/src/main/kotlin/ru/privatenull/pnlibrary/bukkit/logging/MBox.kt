package ru.privatenull.pnlibrary.bukkit.logging

import org.bukkit.ChatColor
import ru.privatenull.pnlibrary.bukkit.banner.PluginBanner
import java.util.Collections
import java.util.logging.Level

class MBox(
    private val logger: PluginLogger,
    val title: String,
) {
    private val _entries = linkedMapOf<String, PluginBanner.Entry>()
    private val _errors = linkedMapOf<String, Throwable>()
    private var shown = false

    val entries: Map<String, PluginBanner.Entry> get() = Collections.unmodifiableMap(_entries)

    fun ok(component: String, details: String? = null): MBox = entry(component, PluginBanner.Status.OK, details)
    fun warn(component: String, details: String? = null): MBox = entry(component, PluginBanner.Status.WARN, details)
    fun fail(component: String, details: String? = null): MBox = entry(component, PluginBanner.Status.FAIL, details)
    fun fail(component: String, error: Throwable): MBox {
        entry(component, PluginBanner.Status.FAIL, error.message ?: error.javaClass.simpleName)
        _errors[component] = error
        return this
    }
    fun skip(component: String, details: String? = null): MBox = entry(component, PluginBanner.Status.SKIP, details)

    fun componentStatus(component: String, active: Boolean): MBox =
        if (active) ok(component, "Активен") else skip(component, "Не настроен")

    fun entry(component: String, status: PluginBanner.Status, details: String? = null): MBox {
        check(!shown) { "MBox has already been shown" }
        _entries[component.trim()] = PluginBanner.Entry(status, details)
        if (status != PluginBanner.Status.FAIL) _errors.remove(component.trim())
        return this
    }

    fun show() {
        check(!shown) { "MBox has already been shown" }
        shown = true

        val console = logger.plugin.server.consoleSender
        val overallStatus = overallStatus()
        val color = overallStatus.color

        console.sendMessage("")
        console.sendMessage("${color}/\\_/\\")
        console.sendMessage("${color}( ${face(overallStatus)} )   ${ChatColor.AQUA}${logger.plugin.name}${ChatColor.DARK_GRAY} > ${ChatColor.WHITE}$title")
        console.sendMessage("${color}> ^ <")
        console.sendMessage("")

        for ((comp, entry) in _entries) {
            console.sendMessage("${ChatColor.DARK_GRAY}          > ${ChatColor.WHITE}$comp${ChatColor.DARK_GRAY}  ${entry.status.color}[ ${entry.status.displayName} ]")
            if (!entry.details.isNullOrBlank()) {
                console.sendMessage("${ChatColor.DARK_GRAY}            └ ${ChatColor.GRAY}${entry.details}")
            }
        }
        console.sendMessage("")

        for ((comp, err) in _errors) {
            logger.delegate.log(Level.SEVERE, "$title — $comp", err)
        }
    }

    fun log() = show()

    private fun overallStatus(): PluginBanner.Status {
        if (_entries.values.any { it.status == PluginBanner.Status.FAIL }) return PluginBanner.Status.FAIL
        if (_entries.values.any { it.status == PluginBanner.Status.WARN }) return PluginBanner.Status.WARN
        if (_entries.isNotEmpty() && _entries.values.all { it.status == PluginBanner.Status.SKIP }) return PluginBanner.Status.SKIP
        return PluginBanner.Status.OK
    }

    private fun face(status: PluginBanner.Status): String = when (status) {
        PluginBanner.Status.OK -> "^.^"
        PluginBanner.Status.WARN -> "o.o"
        PluginBanner.Status.FAIL -> "x.x"
        PluginBanner.Status.SKIP -> "-.-"
    }
}
