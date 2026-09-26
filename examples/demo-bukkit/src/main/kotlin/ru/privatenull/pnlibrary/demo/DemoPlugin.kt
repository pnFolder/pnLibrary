package ru.privatenull.pnlibrary.demo

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.api.currency.Currency
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
import ru.privatenull.pnlibrary.api.plugin.DownloadPolicy
import ru.privatenull.pnlibrary.api.plugin.DenyAction
import ru.privatenull.pnlibrary.api.plugin.ModuleContext
import ru.privatenull.pnlibrary.api.plugin.PluginRegistration
import ru.privatenull.pnlibrary.api.plugin.RemotePolicy
import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider
import ru.privatenull.pnlibrary.api.tasks.TaskSpec
import ru.privatenull.pnlibrary.api.updates.ProductDescriptor
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import ru.privatenull.pnlibrary.localization.MinecraftLocalization
import java.util.concurrent.atomic.AtomicLong
import java.time.Duration

/** Runnable pnLibrary showcase. Lifecycle wiring stays here; capabilities live in separate files. */
class DemoPlugin : JavaPlugin() {
    lateinit var context: ModuleContext; private set
    lateinit var state: DemoState; private set
    lateinit var currency: Currency; private set
    var localization: MinecraftLocalization? = null; private set
    private var command: CommandRegistration? = null
    private var pulse: AutoCloseable? = null
    private var pluginRegistration: PluginRegistration? = null

    override fun onEnable() {
        saveDefaultConfig()
        val library = PnLibraryProvider.getOrNull()
        if (library == null) {
            logger.severe("pnLibrary is not available; disabling pnLibraryDemo")
            server.pluginManager.disablePlugin(this)
            return
        }
        state = DemoState(AtomicLong())


        pluginRegistration = library.plugins.register(this)
        context = pluginRegistration!!.registerModule("pndemo") { builder ->
            builder.product(
                ProductDescriptor.builder("pndemo", description.version)
                    .pnLibraryApi(1, 1)
                    .build()
            )
            config.getString("remote-policy.source")?.takeIf(String::isNotBlank)?.let { source ->
                builder.remotePolicy(RemotePolicy.builder()
                    .source(source)
                    .checkEvery(Duration.ofHours(6))
                    .onDeny(DenyAction.DISABLE_PLUGIN)
                    .value("demo", "pndemo")
                    .build())
            }
            builder.metrics(32592, true) { metrics ->
                metrics.simplePie("server_platform") {
                    server.name
                }
                metrics.singleLineChart("demo_joins") {
                    state.joins.get().toInt()
                }
            }
            builder.updates("pnFolder", "pnLibrary") { updates ->
                updates.channel(UpdateChannel.DEV)
                    .automaticDownload(true)
                    .artifact("(?i)^pnLibrary-demo-bukkit-.*\\.jar$", minimumJava = 8)
            }
            builder.dependency { dependencies ->
                dependencies.product("pnlibrary") { dependency ->
                    dependency.minimumVersion("1.0.0")
                        .github("pnFolder", "pnLibrary")
                        .required(true)
                        .downloadPolicy(DownloadPolicy.AUTOMATIC)
                }
            }
            builder.diagnostics(dataFolder.toPath(), DiagnosticContainer.builder("pndemo")
                .snapshot(DemoDiagnostics.snapshot(state, library))
                .configuration("config.yml").build())
            DemoDeclarations.configure(builder, dataFolder.toPath())
        }
        context.events.register(DemoLibraryEvents(this))
        context.services.register(DemoPlugin::class.java, this)
        currency = DemoCurrency.register(context, state)
        DemoConfig.register(context).load()
        DemoPlaceholders.register(context, currency, state)
        localization = DemoLocalization.start(this)
        DemoUpdateFeature.smoke(this)
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
        pluginRegistration?.close()
        pluginRegistration = null
    }
}
