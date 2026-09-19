package ru.privatenull.pnlibrary.spi.commands

import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import java.util.concurrent.CompletionStage

/** Native command registration boundary implemented by each platform runtime. */
interface PlatformCommandAdapter : AutoCloseable {
    fun register(
        owner: Any,
        command: CommandDefinition,
        dispatcher: PlatformCommandDispatcher,
    ): PlatformCommandRegistration

    override fun close() = Unit
}

/** Shared execution and completion dispatcher called by native delegates. */
interface PlatformCommandDispatcher {
    fun execute(command: CommandDefinition, context: CommandContext): CompletionStage<Void>
    fun suggest(command: CommandDefinition, context: CommandContext): CompletionStage<List<String>>
}

/** Native registration handle owned by the shared command service. */
fun interface PlatformCommandRegistration : AutoCloseable {
    override fun close()
}

/** Default used until a platform installs its native command bridge. */
object UnsupportedPlatformCommandAdapter : PlatformCommandAdapter {
    override fun register(
        owner: Any,
        command: CommandDefinition,
        dispatcher: PlatformCommandDispatcher,
    ): PlatformCommandRegistration = throw UnsupportedOperationException(
        "Command registration is not available on this platform adapter",
    )
}
