package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.commands.BadUsage
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandError
import org.pnfolder.pnlibrary.api.commands.CommandErrorHandler
import org.pnfolder.pnlibrary.api.commands.CommandFailed
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.commands.ConsoleOnly
import org.pnfolder.pnlibrary.api.commands.InvalidArgument
import org.pnfolder.pnlibrary.api.commands.NoPermission
import org.pnfolder.pnlibrary.api.commands.OnCooldown
import org.pnfolder.pnlibrary.api.commands.PlayerNotFound
import org.pnfolder.pnlibrary.api.commands.PlayerOnly
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.util.concurrent.ConcurrentHashMap

/** Shown when no level of the chain sets a usage line; `{usage}` is the generated usage text. */
internal const val DEFAULT_USAGE = "Использование: {usage}"

/** Handlers of one error class each. */
internal typealias ErrorHandlers = Map<Class<out CommandError>, CommandErrorHandler<*>>

/**
 * Handlers that a plugin, or one module of it, sets for all of its commands. A module's scope has its plugin's scope
 * as [parent], so the chain runs subcommand, command, module, plugin, then the library's standard. The handlers are
 * read on every error, so a plugin may set them at any time.
 */
internal class CommandFeedbackScope(val parent: CommandFeedbackScope? = null) {
    val handlers = ConcurrentHashMap<Class<out CommandError>, CommandErrorHandler<*>>()
}

/** The handler for [error] among [handlers]: the one of its own class, else of the nearest base class, else `null`. */
internal fun handlerFor(handlers: ErrorHandlers, error: CommandError): CommandErrorHandler<CommandError>? {
    var type: Class<*>? = error.javaClass
    while (type != null && CommandError::class.java.isAssignableFrom(type)) {
        @Suppress("UNCHECKED_CAST")
        val handler = handlers[type] as CommandErrorHandler<CommandError>?
        if (handler != null) return handler
        type = type.superclass
    }
    return null
}

/**
 * The library's own standard answers, the last link of the chain, taken from `config.yml`: one place that gives
 * every plugin the same wording and sound until a plugin chooses otherwise. An error with its own default message
 * says that; any other is answered with the text of its nearest built-in base class, and the last resort is the
 * text for [CommandFailed].
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
    cooldownMessage: String = "Подождите {seconds} с перед повторным использованием.",
    failedMessage: String = "Не удалось выполнить команду.",
) {
    private val texts: Map<Class<*>, String> = mapOf(
        NoPermission::class.java to deniedMessage,
        BadUsage::class.java to usageFormat,
        InvalidArgument::class.java to invalidArgumentMessage,
        PlayerNotFound::class.java to playerNotFoundMessage,
        PlayerOnly::class.java to playerOnlyMessage,
        ConsoleOnly::class.java to consoleOnlyMessage,
        OnCooldown::class.java to cooldownMessage,
        CommandFailed::class.java to failedMessage,
    )

    /** Sends the standard text for [error], with its placeholders filled in. */
    fun answer(context: CommandContext, error: CommandError, help: List<String> = emptyList()) {
        var type: Class<*>? = error.javaClass
        var template = error.defaultMessage
        while (template == null && type != null) {
            template = texts[type]
            type = type.superclass
        }
        val text = error.format(template ?: texts.getValue(CommandFailed::class.java))
        val tone = if (error is NoPermission || error is OnCooldown) ChatTone.WARNING else ChatTone.FAILURE
        if (error is BadUsage) context.sender.usageNotice(text, help) else context.sender.chatNotice(tone, text)
        if (error is NoPermission) deniedSound?.let { context.sender.playSound(it) }
    }
}

/** Builds the standard answers from the library configuration; an unusable sound name simply means no sound. */
internal fun standardCommandFeedback(config: PnLibraryConfig): StandardCommandFeedback = StandardCommandFeedback(
    deniedMessage = config.noPermissionMessage,
    deniedSound = config.noPermissionSound.takeIf { it.isNotBlank() }
        ?.let { name -> runCatchingNonFatal { Sound.sound(Key.key(name), Sound.Source.MASTER, 1f, 1f) }.getOrNull() },
    usageFormat = config.usageMessage,
    invalidArgumentMessage = config.invalidArgumentMessage,
    playerNotFoundMessage = config.playerNotFoundMessage,
    playerOnlyMessage = config.playerOnlyMessage,
    consoleOnlyMessage = config.consoleOnlyMessage,
)

/** Sends [text] to this sender as a marked notice, the look of every other chat message of the library. */
internal fun CommandSender.chatNotice(tone: ChatTone, text: String, detail: String? = null) {
    ChatKit(ChatReader.of(this)).notice(ChatNotice(tone, text, detail)).forEach(::send)
}

/** A usage line: the mark, the text, and each `<placeholder>` in the accent; a click puts the command in the chat box. */
internal fun CommandSender.usageNotice(text: String, help: List<String> = emptyList()) {
    val kit = ChatKit(ChatReader.of(this))
    val placeholder = Regex("""<[^>]+>|\[[^]]+]""")
    var line = kit.mark(ChatTone.WARNING)
    var from = 0
    placeholder.findAll(text).forEach { found ->
        line = line.append(kit.text(text.substring(from, found.range.first), ChatPalette.TEXT)).append(kit.text(found.value, ChatPalette.ACCENT))
        from = found.range.last + 1
    }
    line = line.append(kit.text(text.substring(from), ChatPalette.TEXT))
    val command = Regex("""/\S.*""").find(text)?.value?.substringBefore('<')?.trimEnd()
    if (command != null && kit.reader.interactive) {
        line = line.clickEvent(ClickEvent.suggestCommand("$command "))
            .hoverEvent(kit.tooltip(kit.text("Нажмите, чтобы вставить команду", ChatPalette.TEXT), kit.text(command, ChatPalette.LABEL)))
    }
    send(line)
    help.forEach { send(kit.text("    $it", ChatPalette.LABEL)) }
}
