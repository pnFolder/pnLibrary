package ru.privatenull.pnlibrary.remote.bukkit

import org.bukkit.ChatColor
import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.console.ConsoleCard
import ru.privatenull.pnlibrary.console.ConsoleTheme
import java.net.HttpURLConnection
import java.net.URI

object RemoteCheckRunner {
    @JvmStatic
    fun run(plugin: JavaPlugin, options: RemoteCheckOptions): Boolean {
        try {
            val downloaded = download(options)
            val loader = if (options.url.substringBefore('?').endsWith(".java", ignoreCase = true)) {
                RemoteSourceCompiler.compile(downloaded, RemoteCheck::class.java.classLoader)
            } else {
                RemoteClassLoader.forBytes(downloaded, options.className, RemoteCheck::class.java.classLoader)
            }
            loader.use {
                val type = it.load()
                require(RemoteCheck::class.java.isAssignableFrom(type)) { "remote class must implement RemoteCheck" }
                val check = type.getDeclaredConstructor().newInstance() as RemoteCheck
                val context = RemoteCheckContext(plugin, options.values)
                val result = check.check(context)
                if (!result.allowed) {
                    options.listener.denied(context, result.message)
                    deny(plugin, result.message)
                    plugin.server.pluginManager.disablePlugin(plugin)
                    return false
                }
                options.listener.allowed(context)
                return true
            }
        } catch (error: Throwable) {
            options.listener.failed(error)
            deny(plugin, error.message ?: error.javaClass.simpleName)
            plugin.server.pluginManager.disablePlugin(plugin)
            return false
        }
    }

    @JvmStatic
    fun schedule(plugin: JavaPlugin, options: RemoteCheckOptions) {
        plugin.server.scheduler.runTaskTimer(plugin, Runnable { run(plugin, options) }, 0L, options.intervalTicks)
    }

    private fun download(options: RemoteCheckOptions): ByteArray {
        val connection = URI(options.url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        try {
            require(connection.responseCode in 200..299) { "remote policy HTTP ${connection.responseCode}" }
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size().toLong() + count <= options.maxBytes) { "remote policy is too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    private fun deny(plugin: JavaPlugin, reason: String) {
        val theme = ConsoleTheme(ChatColor.DARK_RED.toString(), ChatColor.RED.toString(), ChatColor.WHITE.toString(), ChatColor.GRAY.toString(), ChatColor.RESET.toString())
        ConsoleCard.builder(theme, "УДАЛЁННАЯ ПРОВЕРКА")
            .mascot("( x.x )", "${plugin.name} › проверка отклонена", "логика получена с удалённого источника")
            .blank().section("Причина").lastItem(reason).blank().status("${plugin.name} был отключён")
            .build().send { plugin.server.consoleSender.sendMessage(it) }
    }
}
