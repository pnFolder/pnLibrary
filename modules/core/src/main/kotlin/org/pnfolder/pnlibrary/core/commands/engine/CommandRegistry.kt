package org.pnfolder.pnlibrary.core.commands.engine

import org.pnfolder.pnlibrary.api.chat.style.ChatTone
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeKind
import org.pnfolder.pnlibrary.api.commands.definition.SenderRule
import org.pnfolder.pnlibrary.api.commands.definition.completedExecution
import org.pnfolder.pnlibrary.api.commands.definition.completedSuggestions
import org.pnfolder.pnlibrary.api.commands.error.CommandError
import org.pnfolder.pnlibrary.api.commands.error.CommandFailed
import org.pnfolder.pnlibrary.api.commands.error.CommandFailure
import org.pnfolder.pnlibrary.api.commands.error.NoPermission
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.common.lang.Failures
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandDispatcher
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import java.util.Collections
import java.util.IdentityHashMap
import java.util.LinkedHashSet
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal const val DEFAULT_NO_PERMISSION = "Недостаточно прав для этой команды."

internal class CommandRegistry(
    private val commandAdapter: PlatformCommandAdapter,
    private val logging: PlatformLogging,
    private val standard: StandardCommandFeedback = StandardCommandFeedback(),
) : CommandService, PlatformCommandDispatcher, AutoCloseable {
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
            Failures.rethrowIfFatal(error)
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
            Failures.rethrowIfFatal(error)
            failed(registration, executable, error)
            return completedExecution()
        }
        return stage.handle { _, error ->
            if (error != null) failed(registration, executable, unwrap(error))
            null
        }
    }

    // CommandFailure = intentional error, answered through the normal chain. Anything else is logged and shown as a failure.
    private fun failed(registration: Registration, executable: CommandRoute.Executable, error: Throwable) {
        val failure = generateSequence(error) { it.cause }.filterIsInstance<CommandFailure>().firstOrNull()
        if (failure == null) {
            executionFailed(registration.owner, registration.command.name, executable.context, error)
            return
        }
        answer(registration, executable.context, failure.error, executable.path.asReversed())
    }

    // Error waterfall: deepest node → parent nodes → module → plugin → library standard.
    // At each level the handler for the exact error class wins, then the nearest base class.
    private fun answer(registration: Registration, context: CommandContext, error: CommandError, nodes: List<CommandNode>) {
        fun handledBy(handlers: ErrorHandlers): Boolean {
            val handler = handlerFor(handlers, error) ?: return false
            return react(registration) { handler.handle(context, error) }
        }
        for (node in nodes) {
            if (handledBy(node.errorHandlers)) return
            val text = node.noPermissionMessage
            if (error is NoPermission && text != null && react(registration) { context.sender.chatNotice(ChatTone.WARNING, text) }) return
        }
        var scope = registration.feedback
        while (scope != null) {
            if (handledBy(scope.handlers)) return
            scope = scope.parent
        }
        react(registration) { standard.answer(context, error, helpFor(nodes.firstOrNull(), context)) }
    }

    private fun helpFor(node: CommandNode?, context: CommandContext): List<String> {
        if (node == null) return emptyList()
        val ways = node.children
            .filter { child ->
                child.description.isNotEmpty() &&
                    child.permission.let { needed -> needed == null || context.sender.hasPermission(needed) } &&
                    when (child.senderRule) {
                        SenderRule.PLAYER -> !context.sender.isConsole
                        SenderRule.CONSOLE -> context.sender.isConsole
                        SenderRule.ANY -> true
                    }
            }
            .map { (if (it.kind == CommandNodeKind.ARGUMENT) "<${it.name}>" else it.name) + " — " + it.description.first() }
        return node.description + ways
    }

    // A broken handler logs and returns false so the next level answers — the sender never sees handler exceptions.
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
            Failures.rethrowIfFatal(error)
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
        runCatchingNonFatal { commandAdapter.close() }.onFailure { error ->
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
        standard.answer(context, CommandFailed())
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
            runCatchingNonFatal(registration::close).onFailure { error ->
                logging.log(registration.owner, LogLevel.ERROR, "Command cleanup failed", error)
            }
        }
    }

    // Strips the CompletionException wrapper added by the stage; keeps the original cause.
    private fun unwrap(error: Throwable): Throwable = if (error is CompletionException) error.cause ?: error else error

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
