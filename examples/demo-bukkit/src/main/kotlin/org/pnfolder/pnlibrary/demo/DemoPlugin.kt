package org.pnfolder.pnlibrary.demo

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.currency.Currency
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticContainer
import org.pnfolder.pnlibrary.api.platform.platform
import org.pnfolder.pnlibrary.api.plugin.DenyAction
import org.pnfolder.pnlibrary.api.plugin.DownloadPolicy
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration
import org.pnfolder.pnlibrary.api.plugin.RemotePolicySettings
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import org.pnfolder.pnlibrary.api.updates.ProductDescriptor
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform
import org.pnfolder.pnlibrary.localization.MinecraftLocalization
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

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
        val bukkitPlatform = library.platform<BukkitPlatform>()
        state = DemoState(AtomicLong())

        pluginRegistration = library.plugins.register(this)
        context = pluginRegistration!!.registerModule("pndemo") { builder ->
            builder.product(
                ProductDescriptor.builder().version(description.version)
                    .pnLibraryApi(1, 1)
                    .build()
            )
            config.getString("remote-policy.source")?.takeIf(String::isNotBlank)?.let { source ->
                builder.remotePolicy(RemotePolicySettings.builder()
                    .source(source)
                    .checkEvery(Duration.ofHours(6))
                    .onDeny(DenyAction.DISABLE_PLUGIN)
                    .value("demo", "pndemo")
                    .build())
            }
            builder.metrics { configuration ->
                configuration.bStats(32592)
                configuration.charts { metrics ->
                    metrics.simplePie("server_platform") {
                        bukkitPlatform.serverInfo.displayName
                    }
                    metrics.singleLineChart("demo_joins") {
                        state.joins.get().toInt()
                    }
                }
            }
            builder.updates("pnFolder", "pnLibrary") { updates ->
                updates.channel(UpdateChannel.DEV)
                    .automaticDownload(true)
                    .artifact("(?i)^pnLibrary-demo-bukkit-.*\\.jar$", minimumJava = 8)
            }
            builder.dependencies { dependencies ->
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
        command = DemoCommands.registerPortable(library, context, currency)
        pulse = context.tasks.schedule(TaskSpec.builder().name("demo-pulse").key("demo-pulse")
            .interval(Duration.ofSeconds(30))
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
