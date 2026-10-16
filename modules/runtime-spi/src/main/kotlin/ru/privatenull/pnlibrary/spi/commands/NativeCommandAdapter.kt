package ru.privatenull.pnlibrary.spi.commands

import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import java.util.concurrent.CompletionStage

/** Native source-specific registration boundary shared by every platform runtime. */
interface NativeCommandRegistrar<S> {
    /**
     * Registers [command] for its native [owner] and returns a handle that unregisters it.
     * Callbacks receive the native source, invoked alias, and arguments in that order.
     * Platforms without alias information may pass the canonical command name.
     */
    fun register(
        owner: Any,
        command: CommandDefinition,
        execute: (S, String, Array<String>) -> Unit,
        suggest: (S, String, Array<String>) -> CompletionStage<List<String>>,
    ): PlatformCommandRegistration
}

/** Connects one native source type to the shared portable command dispatcher. */
abstract class GenericPlatformCommandAdapter<S> : TrackingPlatformCommandAdapter() {
    protected abstract val registrar: NativeCommandRegistrar<S>

    protected abstract fun context(
        source: S,
        alias: String,
        arguments: Array<String>,
    ): CommandContext

    final override fun registerNative(
        owner: Any,
        command: CommandDefinition,
        dispatcher: PlatformCommandDispatcher,
    ): PlatformCommandRegistration = registrar.register(
        owner = owner,
        command = command,
        execute = { source, alias, arguments ->
            dispatcher.execute(command, context(source, alias, arguments))
        },
        suggest = { source, alias, arguments ->
            dispatcher.suggest(command, context(source, alias, arguments))
        },
    )
}
