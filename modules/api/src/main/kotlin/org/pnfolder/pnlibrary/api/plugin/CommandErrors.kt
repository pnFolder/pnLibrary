package org.pnfolder.pnlibrary.api.plugin

import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandError
import org.pnfolder.pnlibrary.api.commands.CommandErrorHandler

/**
 * Kotlin form of [ModuleContext.onCommandError]: `context.onCommandError<NoMoney> { error -> sender.send(...) }`.
 * Inside the lambda `this` is the [CommandContext], so `sender` is right there.
 */
inline fun <reified E : CommandError> ModuleContext.onCommandError(noinline handler: CommandContext.(E) -> Unit) =
    onCommandError(E::class.java, CommandErrorHandler<E> { context, error -> context.handler(error) })

/** Kotlin form with plain text: `context.onCommandError<PlayerNotFound>("Игрока {value} нет в сети")`. */
inline fun <reified E : CommandError> ModuleContext.onCommandError(message: String) = onCommandError(E::class.java, message)

/** Kotlin form of [PluginRegistration.onCommandError]: the lambda's `this` is the [CommandContext]. */
inline fun <reified E : CommandError> PluginRegistration.onCommandError(noinline handler: CommandContext.(E) -> Unit) =
    onCommandError(E::class.java, CommandErrorHandler<E> { context, error -> context.handler(error) })

/** Kotlin form of [PluginRegistration.onCommandError] with plain text. */
inline fun <reified E : CommandError> PluginRegistration.onCommandError(message: String) = onCommandError(E::class.java, message)
