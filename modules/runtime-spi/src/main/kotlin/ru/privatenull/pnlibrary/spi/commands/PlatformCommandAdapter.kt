package ru.privatenull.pnlibrary.spi.commands

import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import java.util.concurrent.CompletionStage

/** Native command registration boundary implemented by each platform runtime. */
interface PlatformCommandAdapter : AutoCloseable {
    /** Installs [command] for [owner] and connects it to [dispatcher]. */
    fun register(
        owner: Any,
        command: CommandDefinition,
        dispatcher: PlatformCommandDispatcher,
    ): PlatformCommandRegistration

    /** Releases every native command resource owned by this adapter. */
    override fun close() = Unit
}

/** Shared execution and completion dispatcher called by native delegates. */
interface PlatformCommandDispatcher {
    /** Executes the resolved [command] with portable [context]. */
    fun execute(command: CommandDefinition, context: CommandContext): CompletionStage<Void>
    /** Computes suggestions for [command] with portable [context]. */
    fun suggest(command: CommandDefinition, context: CommandContext): CompletionStage<List<String>>
}

/** Native registration handle owned by the shared command service. */
fun interface PlatformCommandRegistration : AutoCloseable {
    /** Removes the native registration. */
    override fun close()
}

/** Default used until a platform installs its native command bridge. */
object UnsupportedPlatformCommandAdapter : PlatformCommandAdapter {
    /** Returns a no-op registration because no native bridge is installed. */
    override fun register(
        owner: Any,
        command: CommandDefinition,
        dispatcher: PlatformCommandDispatcher,
    ): PlatformCommandRegistration = PlatformCommandRegistration { }
}
