package ru.privatenull.pnlibrary.folia

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.PlatformAdapter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * Platform adapter targeting Folia regional schedulers.
 */
class FoliaPlatformAdapter(
    val plugin: Plugin,
) : PlatformAdapter {

    private val closedFlag = AtomicBoolean(false)

    override val id: String get() = "folia"

    override fun details(): Map<String, Any?> {
        val data = linkedMapOf<String, Any?>()
        data["platform"] = "folia"
        data["serverName"] = Bukkit.getServer().name
        data["serverVersion"] = Bukkit.getServer().version
        data["bukkitVersion"] = Bukkit.getServer().bukkitVersion
        data["onlinePlayers"] = Bukkit.getServer().onlinePlayers.size
        data["worlds"] = Bukkit.getServer().worlds.size
        return data
    }

    override fun executeGlobal(task: Runnable) {
        if (closedFlag.get()) return
        Bukkit.getGlobalRegionScheduler().run(plugin, Consumer { task.run() })
    }

    override fun executeReply(recipient: Any, task: Runnable) {
        if (closedFlag.get()) return
        if (recipient is Player) {
            recipient.scheduler.run(plugin, Consumer { task.run() }, null)
        } else {
            executeGlobal(task)
        }
    }

    override fun close() {
        closedFlag.set(true)
    }
}
