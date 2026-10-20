package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.CommandProblem
import org.pnfolder.pnlibrary.api.commands.CommandProblemHandler
import org.pnfolder.pnlibrary.api.commands.CommandProblemKind
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import java.util.concurrent.ConcurrentHashMap

/** Shown when no level of the chain sets a usage line; `{usage}` is the generated usage text. */
internal const val DEFAULT_USAGE = "Использование: {usage}"

/**
 * Handlers that a plugin, or one module of it, sets for all of its commands. A module's scope has its plugin's scope
 * as [parent], so the chain runs subcommand, command, module, plugin, then the library's standard. The handlers are
 * read on every problem, so a plugin may set them at any time.
 */
internal class CommandFeedbackScope(val parent: CommandFeedbackScope? = null) {
    val handlers = ConcurrentHashMap<CommandProblemKind, CommandProblemHandler>()
}

/**
 * The library's own standard answers, the last link of the chain, taken from `config.yml`: one place that gives
 * every plugin the same wording and sound until a plugin chooses otherwise. A kind without its own text is answered
 * with the text of its parent kind, and the last resort is the text for [CommandProblemKind.FAILED].
 *
 * @property deniedSound sound played with the denial text, or `null` for none
 */
internal class StandardCommandFeedback(
    deniedMessage: String = DEFAULT_NO_PERMISSION,
    val deniedSound: Sound? = null,
    usageFormat: String = DEFAULT_USAGE,
    invalidArgumentMessage: String = "Неверное значение «{value}» для {argument}. {usage}",
    playerNotFoundMessage: String = "Игрок {value} сейчас не в сети.",
    playerOnlyMessage: String = "Эту команду можно использовать только в игре.",
    consoleOnlyMessage: String = "Эту команду можно использовать только из консоли.",
    cooldownMessage: String = "Подождите немного перед повторным использованием.",
    failedMessage: String = "Не удалось выполнить команду.",
) {
    private val texts: Map<CommandProblemKind, String> = mapOf(
        CommandProblemKind.DENIED to deniedMessage,
        CommandProblemKind.USAGE to usageFormat,
        CommandProblemKind.INVALID_ARGUMENT to invalidArgumentMessage,
        CommandProblemKind.PLAYER_NOT_FOUND to playerNotFoundMessage,
        CommandProblemKind.PLAYER_ONLY to playerOnlyMessage,
        CommandProblemKind.CONSOLE_ONLY to consoleOnlyMessage,
        CommandProblemKind.COOLDOWN to cooldownMessage,
        CommandProblemKind.FAILED to failedMessage,
    )

    /** Sends the standard text for the problem's kind, or the nearest parent kind's, with details filled in. */
    fun answer(problem: CommandProblem) {
        val template = problem.kind.lineage.firstNotNullOfOrNull { texts[it] } ?: texts.getValue(CommandProblemKind.FAILED)
        problem.sender.send(Component.text(problem.text(template)))
        if (problem.kind.lineage.contains(CommandProblemKind.DENIED)) deniedSound?.let { problem.sender.playSound(it) }
    }
}

/** Builds the standard answers from the library configuration; an unusable sound name simply means no sound. */
internal fun standardCommandFeedback(config: PnLibraryConfig): StandardCommandFeedback = StandardCommandFeedback(
    deniedMessage = config.noPermissionMessage,
    deniedSound = config.noPermissionSound.takeIf { it.isNotBlank() }
        ?.let { name -> runCatching { Sound.sound(Key.key(name), Sound.Source.MASTER, 1f, 1f) }.getOrNull() },
    usageFormat = config.usageMessage,
    invalidArgumentMessage = config.invalidArgumentMessage,
    playerNotFoundMessage = config.playerNotFoundMessage,
    playerOnlyMessage = config.playerOnlyMessage,
    consoleOnlyMessage = config.consoleOnlyMessage,
)
