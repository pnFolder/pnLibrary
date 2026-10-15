package ru.privatenull.pnlibrary.spi.commands

import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import java.util.concurrent.CompletionStage

/** Native source-specific registration boundary shared by every platform runtime. */
interface NativeCommandRegistrar<S> {
    /** Registers [command] and returns a handle that unregisters it. */
    fun register(
        command: CommandDefinition,
        execute: (S, Array<String>) -> Unit,
        suggest: (S, Array<String>) -> CompletionStage<List<String>>,
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
        command = command,
        execute = { source, arguments ->
            dispatcher.execute(command, context(source, command.name, arguments))
        },
        suggest = { source, arguments ->
            dispatcher.suggest(command, context(source, command.name, arguments))
        },
    )
}
