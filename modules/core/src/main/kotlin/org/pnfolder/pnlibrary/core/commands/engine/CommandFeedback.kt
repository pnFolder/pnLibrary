package org.pnfolder.pnlibrary.core.commands.engine

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.chat.style.ChatTone
import org.pnfolder.pnlibrary.api.commands.error.BadUsage
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.error.CommandError
import org.pnfolder.pnlibrary.api.commands.error.CommandErrorHandler
import org.pnfolder.pnlibrary.api.commands.error.CommandFailed
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.commands.error.ConsoleOnly
import org.pnfolder.pnlibrary.api.commands.error.InvalidArgument
import org.pnfolder.pnlibrary.api.commands.error.NoPermission
import org.pnfolder.pnlibrary.api.commands.error.OnCooldown
import org.pnfolder.pnlibrary.api.commands.error.PlayerNotFound
import org.pnfolder.pnlibrary.api.commands.error.PlayerOnly
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.util.concurrent.ConcurrentHashMap

internal const val DEFAULT_USAGE = "Использование: {usage}"

internal typealias ErrorHandlers = Map<Class<out CommandError>, CommandErrorHandler<*>>

// A module's scope chains to its plugin's scope, so the error waterfall runs: node → module → plugin → library.
internal class CommandFeedbackScope(val parent: CommandFeedbackScope? = null) {
    val handlers = ConcurrentHashMap<Class<out CommandError>, CommandErrorHandler<*>>()
}

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

// Last link of the error chain; falls back through base classes and uses CommandFailed text as a last resort.
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

internal fun standardCommandFeedback(config: PnLibraryConfig): StandardCommandFeedback = StandardCommandFeedback(
    deniedMessage = config.noPermissionMessage,
    deniedSound = config.noPermissionSound.takeIf { it.isNotBlank() }
        ?.let { name -> runCatchingNonFatal { Sound.sound(Key.key(name), Sound.Source.MASTER, 1f, 1f) }.getOrNull() },
    usageFormat = config.usageMessage,
    invalidArgumentMessage = config.invalidArgumentMessage,
    playerNotFoundMessage = config.playerNotFoundMessage,
    playerOnlyMessage = config.playerOnlyMessage,
    consoleOnlyMessage = config.consoleOnlyMessage,
    cooldownMessage = config.cooldownMessage,
    failedMessage = config.commandFailedMessage,
)

internal fun CommandSender.chatNotice(tone: ChatTone, text: String, detail: String? = null) {
    ChatKit(ChatReader.of(this)).notice(ChatNotice(tone, text, detail)).forEach(::send)
}

private val USAGE_PLACEHOLDER = Regex("""<[^>]+>|\[[^]]+]""")
private val USAGE_COMMAND = Regex("""/\S.*""")

internal fun CommandSender.usageNotice(text: String, help: List<String> = emptyList()) {
    val kit = ChatKit(ChatReader.of(this))
    var line = kit.mark(ChatTone.WARNING)
    var from = 0
    USAGE_PLACEHOLDER.findAll(text).forEach { found ->
        line = line.append(kit.text(text.substring(from, found.range.first), ChatPalette.TEXT)).append(kit.text(found.value, ChatPalette.ACCENT))
        from = found.range.last + 1
    }
    line = line.append(kit.text(text.substring(from), ChatPalette.TEXT))
    val command = USAGE_COMMAND.find(text)?.value?.substringBefore('<')?.trimEnd()
    if (command != null && kit.reader.interactive) {
        line = line.clickEvent(ClickEvent.suggestCommand("$command "))
            .hoverEvent(kit.tooltip(kit.text("Click to insert command", ChatPalette.TEXT), kit.text(command, ChatPalette.LABEL)))
    }
    send(line)
    help.forEach { send(kit.text("    $it", ChatPalette.LABEL)) }
}
