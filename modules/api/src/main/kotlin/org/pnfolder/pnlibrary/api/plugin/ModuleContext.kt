package org.pnfolder.pnlibrary.api.plugin

import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMessages
import org.pnfolder.pnlibrary.api.plugin.lifecycle.PluginLifecycle
import org.pnfolder.pnlibrary.api.plugin.control.ModuleControl
import org.pnfolder.pnlibrary.api.commands.definition.command
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.scenario.actions.ActionService
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.error.CommandError
import org.pnfolder.pnlibrary.api.commands.error.CommandErrorHandler
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.cooldowns.CooldownService
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticRegistration
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration
import org.pnfolder.pnlibrary.api.events.bus.EventScope
import org.pnfolder.pnlibrary.api.expansions.ExpansionManager
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter
import org.pnfolder.pnlibrary.api.metrics.MetricsController
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.services.ServiceManager
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.text.ComponentService
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import org.pnfolder.pnlibrary.localization.MinecraftLocalization

/** Owns the isolated pnLibrary capabilities of one logical plugin module. */
interface ModuleContext : AutoCloseable {
    /** Registers a command released when this module closes. */
    fun registerCommand(command: CommandDefinition): CommandRegistration =
        throw UnsupportedOperationException("Module commands are not available in this runtime")

    /**
     * Answers errors of class [type] for every command of this module with [handler]. The most specific level
     * wins: a handler on a command or subcommand over this one, this one over the plugin-wide answer of
     * [PluginRegistration.onCommandError], over the library's standard
     * answer from the library's settings.
     */
    fun <E : CommandError> onCommandError(type: Class<E>, handler: CommandErrorHandler<E>) {
        throw UnsupportedOperationException("Module commands are not available in this runtime")
    }

    /** Answers errors of class [type] for every command of this module with the plain [message]. */
    fun <E : CommandError> onCommandError(type: Class<E>, message: String) {
        val text = message.trim()
        onCommandError(type, CommandErrorHandler<E> { context, error -> context.sender.send(Component.text(error.format(text))) })
    }

    /** Builds a command whose lifecycle belongs to this module. */
    fun registerCommand(name: String, configure: CommandBuilder.() -> Unit): CommandRegistration =
        registerCommand(command(name, configure))

    /** Registry-wide stable identity used by shared services and diagnostics. */
    val key: PluginId
    /** Local identity unique within the owning platform plugin. */
    val id: ModuleId
    /** Immutable platform and display metadata for this module. */
    val metadata: PluginMetadata
    /** Buffered lifecycle-message factory. */
    val lifecycle: PluginLifecycle
    /** Buffered arbitrary-message factory. */
    val messages: PluginMessages
    /** Owner-bound event subscriptions. */
    val events: EventScope
    /** Owner-bound scheduled tasks. */
    val tasks: TaskScope
    /** Module-local service registry. */
    val services: ServiceManager
    /**
     * Official Minecraft translations, the only way a plugin reaches them. Loading is shared by every plugin, so a
     * version and locale one plugin loaded is cached for all. Closing this view does nothing, and keyboard layouts
     * registered through its `layouts` are removed when this module closes; no handle needs closing.
     *
     * Available on game servers only: on a proxy this throws [UnsupportedOperationException].
     */
    val localization: MinecraftLocalization
        get() = throw UnsupportedOperationException("Minecraft localization is not available on this platform")
    /**
     * The library's expansions with ownership: what this module registers through it is disabled and unregistered when
     * the module closes, with no handle to keep. Lookups read the same registry as `PnLibrary.expansions`, and ids stay
     * unique across the library.
     */
    val expansions: ExpansionManager
        get() = throw UnsupportedOperationException("Expansions are not available in this runtime")
    /** Module-bound structured logger. */
    val logger: PnLogger
    /** Module-bound configuration scope. */
    val configs: ConfigScope
    /** Portable configurable-action service. */
    val actions: ActionService
    /** Module-local placeholder registry. */
    val placeholders: PlaceholderService
    /** Adventure component parsing and rendering service. */
    val components: ComponentService
    /** Module-local cooldown service. */
    val cooldowns: CooldownService
    /** Runtime metrics control for this module. */
    val metrics: MetricsController
    /** Normalized error capture shared by local diagnostics and remote providers. */
    val errors: ErrorReporter
    /** Diagnostics registration, or `null` when diagnostics were not configured. */
    val diagnostics: DiagnosticRegistration?
    /** Update registration, or `null` when updates were not configured. */
    val updates: UpdateRegistration?
    /** Auxiliary download registration, or `null` when none was configured. */
    val downloads: DownloadRegistration?
    /** Switches this module on and off; the same operations as `/pn modules` and the menu. */
    val control: ModuleControl
        get() = throw UnsupportedOperationException("Module control is not available in this runtime")
    /** Whether this module has released all owned resources. */
    val isClosed: Boolean
    /** Releases every capability and registration owned by this module. */
    override fun close()
}
