package org.pnfolder.pnlibrary.api.plugin

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.CommandProblemHandler
import org.pnfolder.pnlibrary.api.commands.CommandProblemKind
import java.util.function.Consumer

/** Registration of one native platform plugin and all logical modules it owns. */
interface PluginRegistration : AutoCloseable {
    /** Native platform plugin owning this registration. */
    val owner: Any
    /** Whether this registration has released its modules. */
    val isClosed: Boolean

    /**
     * Answers problems of [kind] for every command of this plugin, in all of its modules, with [handler]. A module,
     * command or subcommand that sets its own answer wins; this wins over the library's standard answer from
     * `config.yml`.
     */
    fun onCommandProblem(kind: CommandProblemKind, handler: CommandProblemHandler) {
        throw UnsupportedOperationException("Plugin commands are not available in this runtime")
    }

    /** Answers problems of [kind] for every command of this plugin with the plain [message]. */
    fun onCommandProblem(kind: CommandProblemKind, message: String) {
        onCommandProblem(kind, CommandProblemHandler { problem -> problem.sender.send(Component.text(problem.text(message.trim()))) })
    }

    /** Answers a sender who may not use any command of this plugin; shorthand for [onCommandProblem] with `DENIED`. */
    fun onCommandDenied(handler: CommandProblemHandler) = onCommandProblem(CommandProblemKind.DENIED, handler)

    /** Answers a mistyped command of this plugin; shorthand for [onCommandProblem] with `USAGE`. */
    fun onCommandUsage(handler: CommandProblemHandler) = onCommandProblem(CommandProblemKind.USAGE, handler)

    /** Sets the plain text for a denied sender of any command of this plugin. */
    fun commandNoPermissionMessage(text: String) = onCommandProblem(CommandProblemKind.DENIED, text)

    /** Registers a logical module identified by [id]. */
    fun registerModule(
        id: ModuleId,
        configure: Consumer<PluginBuilder> = Consumer { },
    ): ModuleContext

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

    /** Closes every owned module and releases this registration. */
    override fun close()
}
