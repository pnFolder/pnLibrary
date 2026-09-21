package ru.privatenull.pnlibrary.demo

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.api.currency.Currency
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
import ru.privatenull.pnlibrary.api.plugin.PluginContext
import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider
import ru.privatenull.pnlibrary.api.tasks.TaskSpec
import ru.privatenull.pnlibrary.localization.MinecraftLocalization
import java.util.concurrent.atomic.AtomicLong

/** Runnable pnLibrary showcase. Lifecycle wiring stays here; capabilities live in separate files. */
class DemoPlugin : JavaPlugin() {
    lateinit var context: PluginContext; private set
    lateinit var state: DemoState; private set
    lateinit var currency: Currency; private set
    var localization: MinecraftLocalization? = null; private set
    private var command: CommandRegistration? = null
    private var pulse: AutoCloseable? = null

    override fun onEnable() {
        saveDefaultConfig()
        val library = PnLibraryProvider.getOrNull()
        if (library == null) {
            logger.severe("pnLibrary is not available; disabling pnLibraryDemo")
            server.pluginManager.disablePlugin(this)
            return
        }
        state = DemoState(AtomicLong())
        context = library.plugins.register(this) { builder ->
            builder.metrics(32592, true) { metrics ->
                metrics.simplePie("server_platform") { server.name }
                metrics.singleLineChart("demo_joins") { state.joins.get().toInt() }
            }
            builder.diagnostics(dataFolder.toPath(), DiagnosticContainer.builder("pndemo")
                .snapshot(DemoDiagnostics.snapshot(state, library))
                .configuration("config.yml").build())
            builder.listener(DemoLibraryEvents(this))
            DemoDeclarations.configure(builder, dataFolder.toPath())
        }
        context.services.register(DemoPlugin::class.java, this)
        currency = DemoCurrency.register(context, state)
        DemoConfig.register(context).load()
        DemoPlaceholders.register(context, currency, state)
        localization = DemoLocalization.start(this)
        command = DemoCommands.registerPortable(this, library, context, currency)
        pulse = context.tasks.schedule(TaskSpec.builder().name("demo-pulse").key("demo-pulse")
            .interval(java.time.Duration.ofSeconds(30))
            .action { context.logger.info("pulse: online=${Bukkit.getOnlinePlayers().size}, joins=${state.joins.get()}") }
            .build())
        getCommand("pndemo")?.let { native ->
            val handler = DemoCommands.Native(this, context, currency, state)
            native.setExecutor(handler)
            native.tabCompleter = handler
        }
        server.pluginManager.registerEvents(DemoBukkitEvents(this), this)
        context.lifecycle.enabled().ok("Context", context.id.value).ok("Currency", currency.key.toString())
            .ok("Placeholder", "pndemo_coins").ok("Tasks", "demo-pulse").show()
    }

    override fun onDisable() {
        command?.close(); pulse?.close(); localization?.close()
        if (::context.isInitialized) context.close()
    }
}
