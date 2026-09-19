package ru.privatenull.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.api.commands.CommandService
import ru.privatenull.pnlibrary.api.commands.completedExecution
import ru.privatenull.pnlibrary.api.commands.completedSuggestions
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandDispatcher
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandRegistration
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import java.util.Collections
import java.util.IdentityHashMap
import java.util.LinkedHashSet
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

/** Shared validation, permission, dispatch, and lifecycle engine for portable commands. */
internal class CommandServiceImpl(
    private val platform: PlatformAdapter,
) : CommandService, PlatformCommandDispatcher, AutoCloseable {
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val reservedNames = linkedSetOf<String>()
    private val registrations = LinkedHashSet<Registration>()
    private val byDefinition = IdentityHashMap<CommandDefinition, Registration>()
    private val byOwner = IdentityHashMap<Any, MutableSet<Registration>>()

    override fun register(owner: Any, command: CommandDefinition): CommandRegistration {
        val names = linkedSetOf(command.name).apply { addAll(command.aliases) }
        synchronized(lock) {
            check(!closed.get()) { "CommandService is closed" }
            val collision = names.firstOrNull(reservedNames::contains)
            require(collision == null) { "Command name or alias '$collision' is already registered" }
            reservedNames += names
        }

        val native = try {
            platform.commandAdapter.register(owner, command, this)
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
        owned.asReversed().forEach(Registration::close)
    }

    override fun execute(
        command: CommandDefinition,
        context: CommandContext,
    ): CompletionStage<Void> {
        val registration = activeRegistration(command) ?: return completedExecution()
        if (!isAllowed(command, context)) {
            context.sender.send(Component.text("You do not have permission."))
            return completedExecution()
        }
        val stage = try {
            command.execution.execute(context)
        } catch (error: Throwable) {
            executionFailed(registration.owner, context, error)
            return completedExecution()
        }
        return stage.handle { _, error ->
            if (error != null) executionFailed(registration.owner, context, unwrap(error))
            null
        }
    }

    override fun suggest(
        command: CommandDefinition,
        context: CommandContext,
    ): CompletionStage<List<String>> {
        val registration = activeRegistration(command) ?: return completedSuggestions(emptyList())
        if (!isAllowed(command, context)) return completedSuggestions(emptyList())
        val stage = try {
            command.suggestions.suggest(context)
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
        current.asReversed().forEach(Registration::close)
        platform.commandAdapter.close()
    }

    private fun activeRegistration(command: CommandDefinition): Registration? =
        synchronized(lock) { byDefinition[command]?.takeUnless { it.isClosed } }

    private fun isAllowed(command: CommandDefinition, context: CommandContext): Boolean {
        val permission = command.permission ?: return true
        return (context.sender.isConsole && command.consoleBypassesPermission) ||
            context.sender.hasPermission(permission)
    }

    private fun executionFailed(owner: Any, context: CommandContext, error: Throwable) {
        platform.log(owner, LogLevel.ERROR, "Command execution failed", error)
        context.sender.send(Component.text("Command execution failed."))
    }

    private fun suggestionFailed(owner: Any, error: Throwable) {
        platform.log(owner, LogLevel.ERROR, "Command suggestions failed", error)
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
