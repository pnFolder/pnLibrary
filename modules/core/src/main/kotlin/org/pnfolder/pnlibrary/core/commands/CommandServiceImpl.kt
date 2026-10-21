package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.commands.CommandError
import org.pnfolder.pnlibrary.api.commands.CommandFailure
import org.pnfolder.pnlibrary.api.commands.CommandNode
import org.pnfolder.pnlibrary.api.commands.NoPermission
import org.pnfolder.pnlibrary.api.commands.completedExecution
import org.pnfolder.pnlibrary.api.commands.completedSuggestions
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandDispatcher
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import java.util.Collections
import java.util.IdentityHashMap
import java.util.LinkedHashSet
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Shared validation, permission, dispatch, and lifecycle engine for portable commands. */
/** Shown when neither the denied node nor its command has a no-permission message and the config sets none. */
internal const val DEFAULT_NO_PERMISSION = "Недостаточно прав для этой команды."

/**
 * Answers a problem with a command from the most specific level that has an answer: the subcommand, the command, the
 * plugin module, the plugin as a whole, and last [standard], the library's own wording from `config.yml`.
 *
 * @param standard the library's reactions, used when nothing more specific answers
 */
internal class CommandServiceImpl(
    private val commandAdapter: PlatformCommandAdapter,
    private val logging: PlatformLogging,
    private val standard: StandardCommandFeedback = StandardCommandFeedback(),
) : CommandService, PlatformCommandDispatcher, AutoCloseable {
    /** Convenience for callers that only configure the no-permission text. */
    constructor(commandAdapter: PlatformCommandAdapter, logging: PlatformLogging, defaultNoPermission: String) :
        this(commandAdapter, logging, StandardCommandFeedback(deniedMessage = defaultNoPermission))

    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val reservedNames = linkedSetOf<String>()
    private val registrations = LinkedHashSet<Registration>()
    private val byDefinition = IdentityHashMap<CommandDefinition, Registration>()
    private val byOwner = IdentityHashMap<Any, MutableSet<Registration>>()
    private val router = CommandTreeRouter()
    private val executionCount = AtomicLong()
    private val suggestionCount = AtomicLong()
    private val executionFailureCount = AtomicLong()
    private val suggestionFailureCount = AtomicLong()
    private val executionsByCommand = linkedMapOf<String, Long>()
    private val failuresByCommand = linkedMapOf<String, Long>()

    override fun register(owner: Any, command: CommandDefinition, feedback: CommandFeedbackScope?): CommandRegistration {
        val names = linkedSetOf(command.name).apply { addAll(command.aliases) }
        synchronized(lock) {
            check(!closed.get()) { "CommandService is closed" }
            val collision = names.firstOrNull(reservedNames::contains)
            require(collision == null) { "Command name or alias '$collision' is already registered" }
            reservedNames += names
        }

        val native = try {
            commandAdapter.register(owner, command, this)
        } catch (error: Throwable) {
            synchronized(lock) { reservedNames.removeAll(names) }
            throw error
        }

        val registration = Registration(owner, command, names, native, feedback)
        synchronized(lock) {
            if (closed.get()) {
                reservedNames.removeAll(names)
                native.close()
                error("CommandService is closed")
            }
            registrations += registration
            byDefinition[command] = registration
            byOwner.getOrPut(owner) { Collections.newSetFromMap(IdentityHashMap()) } += registration
        }
        return registration
    }

    override fun unregisterOwner(owner: Any) {
        val owned = synchronized(lock) { byOwner[owner]?.toList().orEmpty() }
        closeRegistrations(owned.asReversed())
    }

    override fun execute(
        command: CommandDefinition,
        context: CommandContext,
    ): CompletionStage<Void> {
        executionCount.incrementAndGet()
        synchronized(lock) {
            executionsByCommand[command.name] = (executionsByCommand[command.name] ?: 0L) + 1L
        }
        val registration = activeRegistration(command) ?: return completedExecution()
        val route = try {
            router.route(command, context)
        } catch (error: Throwable) {
            executionFailed(registration.owner, registration.command.name, context, error)
            return completedExecution()
        }
        val executable = when (route) {
            is CommandRoute.Executable -> route
            is CommandRoute.Problem -> {
                answer(registration, context, route.error, route.nodes)
                return completedExecution()
            }
        }
        val stage = try {
            executable.node.execution.execute(executable.context)
        } catch (error: Throwable) {
            failed(registration, executable, error)
            return completedExecution()
        }
        return stage.handle { _, error ->
            if (error != null) failed(registration, executable, unwrap(error))
            null
        }
    }

    /**
     * A command that stopped with a [CommandFailure] reports an error of its own: it is answered through the same
     * levels as any other. Anything else is an unexpected error, which is logged and shown as a failure.
     */
    private fun failed(registration: Registration, executable: CommandRoute.Executable, error: Throwable) {
        val failure = generateSequence(error) { it.cause }.filterIsInstance<CommandFailure>().firstOrNull()
        if (failure == null) {
            executionFailed(registration.owner, registration.command.name, executable.context, error)
            return
        }
        answer(registration, executable.context, failure.error, executable.path.asReversed())
    }

    /**
     * Answers [error] from the most specific level that can: the nodes from the deepest involved up to the command,
     * then the plugin module, then the plugin, then the library standard. On each level the handler of the error's
     * own class comes first, then the one of its nearest base class; at a node, a plain no-permission text counts as
     * a handler.
     */
    private fun answer(registration: Registration, context: CommandContext, error: CommandError, nodes: List<CommandNode>) {
        fun handledBy(handlers: ErrorHandlers): Boolean {
            val handler = handlerFor(handlers, error) ?: return false
            return react(registration) { handler.handle(context, error) }
        }
        for (node in nodes) {
            if (handledBy(node.errorHandlers)) return
            val text = node.noPermissionMessage
            if (error is NoPermission && text != null && react(registration) { context.sender.send(Component.text(text)) }) return
        }
        var scope = registration.feedback
        while (scope != null) {
            if (handledBy(scope.handlers)) return
            scope = scope.parent
        }
        react(registration) { standard.answer(context, error) }
    }

    /**
     * Runs a plugin's reaction. When it throws, the failure is logged and `false` returned so the next level answers;
     * the sender never sees a broken handler's error.
     */
    private inline fun react(registration: Registration, reaction: () -> Unit): Boolean =
        try {
            reaction()
            true
        } catch (error: Exception) {
            logging.log(registration.owner, LogLevel.ERROR, "Command feedback handler failed", error)
            false
        }

    override fun suggest(
        command: CommandDefinition,
        context: CommandContext,
    ): CompletionStage<List<String>> {
        suggestionCount.incrementAndGet()
        val registration = activeRegistration(command) ?: return completedSuggestions(emptyList())
        val stage = try {
            router.suggest(command, context)
        } catch (error: Throwable) {
            suggestionFailed(registration.owner, error)
            return completedSuggestions(emptyList())
        }
        return stage.handle { suggestions, error ->
            if (error != null) {
                suggestionFailed(registration.owner, unwrap(error))
                emptyList()
            } else {
                suggestions?.toList().orEmpty()
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val current = synchronized(lock) { registrations.toList() }
        closeRegistrations(current.asReversed())
        runCatching { commandAdapter.close() }.onFailure { error ->
            logging.log(this, LogLevel.ERROR, "Command adapter cleanup failed", error)
        }
    }

    private fun activeRegistration(command: CommandDefinition): Registration? =
        synchronized(lock) { byDefinition[command]?.takeUnless { it.isClosed } }

    private fun executionFailed(owner: Any, commandName: String, context: CommandContext, error: Throwable) {
        executionFailureCount.incrementAndGet()
        synchronized(lock) {
            failuresByCommand[commandName] = (failuresByCommand[commandName] ?: 0L) + 1L
        }
        logging.log(owner, LogLevel.ERROR, "Command execution failed", error)
        context.sender.send(Component.text("Command execution failed."))
    }

    private fun suggestionFailed(owner: Any, error: Throwable) {
        suggestionFailureCount.incrementAndGet()
        logging.log(owner, LogLevel.ERROR, "Command suggestions failed", error)
    }

    fun diagnosticSnapshot(): Map<String, Any?> = synchronized(lock) {
        // Closing a registration marks it closed before acquiring this lock.
        // Capture membership once so names and count describe the same set.
        val current = registrations.filterNot { it.isClosed }
        linkedMapOf(
            "registeredCount" to current.size,
            "registeredNames" to current.flatMap { it.command.aliases + it.command.name }.distinct().sorted(),
            "executionCount" to executionCount.get(),
            "suggestionCount" to suggestionCount.get(),
            "executionFailureCount" to executionFailureCount.get(),
            "suggestionFailureCount" to suggestionFailureCount.get(),
            "executionsByCommand" to executionsByCommand.toSortedMap(),
            "failuresByCommand" to failuresByCommand.toSortedMap(),
        )
    }

    private fun closeRegistrations(current: Iterable<Registration>) {
        current.forEach { registration ->
            runCatching(registration::close).onFailure { error ->
                logging.log(registration.owner, LogLevel.ERROR, "Command cleanup failed", error)
            }
        }
    }

    private fun unwrap(error: Throwable): Throwable = error.cause ?: error

    private inner class Registration(
        val owner: Any,
        override val command: CommandDefinition,
        private val names: Set<String>,
        private val native: PlatformCommandRegistration,
        val feedback: CommandFeedbackScope?,
    ) : CommandRegistration {
        private val registrationClosed = AtomicBoolean(false)
        override val isClosed: Boolean get() = registrationClosed.get()

        override fun close() {
            if (!registrationClosed.compareAndSet(false, true)) return
            synchronized(lock) {
                registrations.remove(this)
                byDefinition.remove(command)
                reservedNames.removeAll(names)
                byOwner[owner]?.let { owned ->
                    owned.remove(this)
                    if (owned.isEmpty()) byOwner.remove(owner)
                }
            }
            native.close()
        }
    }
}
