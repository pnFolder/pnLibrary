package org.pnfolder.pnlibrary.api.plugin

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.error.CommandError
import org.pnfolder.pnlibrary.api.commands.error.CommandErrorHandler
import org.pnfolder.pnlibrary.api.config.ConfigInfo
import org.pnfolder.pnlibrary.api.libraries.ModuleLibraries
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary
import org.pnfolder.pnlibrary.api.plugin.control.PluginModule
import java.util.function.Consumer

/** Registration of one native platform plugin and all logical modules it owns. */
interface PluginRegistration : AutoCloseable {
    /** Native platform plugin owning this registration. */
    val owner: Any
    /** Identifier of the plugin, as in `quests/main:quests.yml`. */
    val id: PluginId
    /** Whether this registration has released its modules. */
    val isClosed: Boolean

    /**
     * Answers errors of class [type] for every command of this plugin, in all of its modules, with [handler]. The most specific level
     * wins: a handler on a command or subcommand over this one, over the library's standard
     * answer from the library's settings.
     */
    fun <E : CommandError> onCommandError(type: Class<E>, handler: CommandErrorHandler<E>) {
        throw UnsupportedOperationException("Plugin commands are not available in this runtime")
    }

    /** Answers errors of class [type] for every command of this plugin, in all of its modules, with the plain [message]. */
    fun <E : CommandError> onCommandError(type: Class<E>, message: String) {
        val text = message.trim()
        onCommandError(type, CommandErrorHandler<E> { context, error -> context.sender.send(Component.text(error.format(text))) })
    }

    /**
     * The libraries of the whole plugin: what is registered here is available to every module of it, so a library several
     * modules need is downloaded once. Modules keep their own in `ModuleContext.libraries`.
     */
    val libraries: ModuleLibraries
        get() = throw UnsupportedOperationException("Plugin libraries are not available in this runtime")

    /** Every library of this plugin: its own and those of each module, without repeats. */
    val allLibraries: List<ModuleLibrary>
        get() = (libraries.loaded + all().flatMap { it.libraries.loaded }).distinct()

    /** Registers a logical module identified by [id]. */
    fun registerModule(
        id: ModuleId,
        configure: Consumer<PluginBuilder> = Consumer { },
    ): ModuleContext

    /**
     * Registers a module that the library can switch: [module]'s `onEnable` runs right away (unless an administrator
     * switched this module off for good), and later `/pn modules` and the menu call its hooks.
     */
    fun registerModule(
        id: ModuleId,
        module: PluginModule,
        configure: Consumer<PluginBuilder> = Consumer { },
    ): ModuleContext = throw UnsupportedOperationException("Module control is not available in this runtime")

    /** Normalizes [id] and registers a switchable module; see [registerModule]. */
    fun registerModule(
        id: String,
        module: PluginModule,
        configure: Consumer<PluginBuilder> = Consumer { },
    ): ModuleContext = registerModule(ModuleId.of(id), module, configure)

    /** Normalizes [id] and registers a logical module. */
    fun registerModule(
        id: String,
        configure: Consumer<PluginBuilder> = Consumer { },
    ): ModuleContext = registerModule(ModuleId.of(id), configure)

    /** Returns the module identified by [id], or `null` when absent. */
    fun getModule(id: ModuleId): ModuleContext?

    /** Normalizes [id] and returns the matching module, when present. */
    fun getModule(id: String): ModuleContext? = getModule(ModuleId.of(id))

    /** Returns the module identified by [id] or fails when absent. */
    fun requireModule(id: ModuleId): ModuleContext =
        getModule(id) ?: error("Module $id is not registered for this plugin")

    /** Normalizes [id] and returns the matching module or fails. */
    fun requireModule(id: String): ModuleContext = requireModule(ModuleId.of(id))

    /** Closes and unregisters the module identified by [id]. */
    fun unregisterModule(id: ModuleId)

    /** Normalizes [id], then closes and unregisters the matching module. */
    fun unregisterModule(id: String) = unregisterModule(ModuleId.of(id))

    /** Returns an immutable snapshot of logical modules owned by this plugin. */
    fun all(): List<ModuleContext>

    /** Every configuration file of every module of this plugin. */
    fun configs(): List<ConfigInfo> = all().flatMap { it.configs.list() }

    /** Closes every owned module and releases this registration. */
    override fun close()
}
