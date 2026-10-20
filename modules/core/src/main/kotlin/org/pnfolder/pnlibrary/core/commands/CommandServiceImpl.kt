package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
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
 * @param defaultNoPermission text for a denial that has no message on its node or command; set in `config.yml`
 */
internal class CommandServiceImpl(
    private val commandAdapter: PlatformCommandAdapter,
    private val logging: PlatformLogging,
    private val defaultNoPermission: String = DEFAULT_NO_PERMISSION,
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

    override fun register(owner: Any, command: CommandDefinition): CommandRegistration {
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

        val registration = Registration(owner, command, names, native)
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
            is CommandRoute.Invalid -> {
                context.sender.send(Component.text("Использование: ${route.usage}"))
                return completedExecution()
            }
            is CommandRoute.Denied -> {
                context.sender.send(
                    Component.text(route.message ?: registration.command.noPermissionMessage ?: defaultNoPermission),
                )
                return completedExecution()
            }
        }
        val stage = try {
            executable.node.execution.execute(executable.context)
        } catch (error: Throwable) {
            executionFailed(registration.owner, registration.command.name, context, error)
            return completedExecution()
        }
        return stage.handle { _, error ->
            if (error != null) executionFailed(registration.owner, registration.command.name, context, unwrap(error))
            null
        }
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
